
import logging
import os
import threading
import time
from contextlib import asynccontextmanager
from pathlib import Path
from uuid import UUID, uuid4

import psycopg
import pytesseract
from fastapi import FastAPI, File, Form, HTTPException, UploadFile
from PIL import Image
from psycopg.types.json import Jsonb


# --------------------------------------------------
# Configurazione
# --------------------------------------------------

BASE_DIR = Path(__file__).resolve().parent
UPLOAD_DIR = Path(
    os.environ.get("OCR_UPLOAD_DIR", str(BASE_DIR / "uploads"))
).expanduser()
UPLOAD_DIR.mkdir(parents=True, exist_ok=True)

TESSERACT_PATH = os.environ.get("TESSERACT_PATH")
if TESSERACT_PATH:
    pytesseract.pytesseract.tesseract_cmd = TESSERACT_PATH

OCR_LANGUAGE = os.environ.get("OCR_LANGUAGE", "ita")
TESSERACT_CONFIG = os.environ.get("TESSERACT_CONFIG", "--psm 3")

SUPPORTED_ENGINES = {
    "tesseract",
    "churro",
    "lighton-1b",
    "lighton-4b",
}

ALLOWED_EXTENSIONS = {
    ".jpg", ".jpeg", ".png", ".tiff", ".webp"
}

POLL_INTERVAL = int(os.environ.get("OCR_POLL_INTERVAL", "2"))
MAX_ATTEMPTS = int(os.environ.get("OCR_MAX_ATTEMPTS", "3"))

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("ocr-service")

worker_stop = threading.Event()
worker_thread = None


def db_connect():
    """Apre una connessione PostgreSQL usando variabili d'ambiente."""
    required = [
        "OCR_DB_HOST",
        "OCR_DB_PORT",
        "OCR_DB_NAME",
        "OCR_DB_USER",
        "OCR_DB_PASSWORD",
    ]

    missing = [name for name in required if not os.environ.get(name)]
    if missing:
        raise RuntimeError(
            "Variabili PostgreSQL mancanti: " + ", ".join(missing)
        )

    return psycopg.connect(
        host=os.environ["OCR_DB_HOST"],
        port=int(os.environ["OCR_DB_PORT"]),
        dbname=os.environ["OCR_DB_NAME"],
        user=os.environ["OCR_DB_USER"],
        password=os.environ["OCR_DB_PASSWORD"],
        connect_timeout=5,
    )


# --------------------------------------------------
# Database
# --------------------------------------------------

def init_database():
    with db_connect() as conn:
        conn.execute("""
                     CREATE TABLE IF NOT EXISTS ocr_jobs (
                                                             job_id UUID PRIMARY KEY,
                                                             status VARCHAR(20) NOT NULL
                         CHECK (status IN (
                                'PENDING',
                                'PROCESSING',
                                'COMPLETED',
                                'FAILED'
                                          )),
                         engine VARCHAR(30) NOT NULL,
                         filenames JSONB NOT NULL,
                         filepaths JSONB NOT NULL,
                         file_count INTEGER NOT NULL,
                         result TEXT,
                         pages JSONB,
                         error TEXT,
                         attempts INTEGER NOT NULL DEFAULT 0,
                         created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                         updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                         started_at TIMESTAMPTZ,
                         completed_at TIMESTAMPTZ
                         )
                     """)

        conn.execute("""
                     CREATE INDEX IF NOT EXISTS idx_ocr_jobs_pending
                         ON ocr_jobs (created_at)
                         WHERE status = 'PENDING'
                     """)


def recover_interrupted_jobs():
    """
    Da usare con una sola istanza del servizio OCR.
    I job interrotti vengono rimessi in coda se hanno tentativi disponibili.
    """
    with db_connect() as conn:
        conn.execute("""
                     UPDATE ocr_jobs
                     SET status = CASE
                                      WHEN attempts < %s THEN 'PENDING'
                                      ELSE 'FAILED'
                         END,
                         error = CASE
                                     WHEN attempts < %s THEN NULL
                                     ELSE COALESCE(
                                             error,
                                             'Numero massimo di tentativi raggiunto dopo un arresto.'
                                          )
                             END,
                         updated_at = NOW(),
                         completed_at = CASE
                                            WHEN attempts >= %s THEN NOW()
                                            ELSE NULL
                             END
                     WHERE status = 'PROCESSING'
                     """, (MAX_ATTEMPTS, MAX_ATTEMPTS, MAX_ATTEMPTS))


# --------------------------------------------------
# OCR
# --------------------------------------------------

def recognize_image(file_path: str, engine: str) -> str:
    if engine == "tesseract":
        with Image.open(file_path) as source:
            image = source.convert("RGB")
            return pytesseract.image_to_string(
                image,
                lang=OCR_LANGUAGE,
                config=TESSERACT_CONFIG,
            )

    # Importazione ritardata: i modelli vengono caricati solo
    # quando serve un motore diverso da Tesseract.
    from multimodel_ocr import transcribe_image

    return transcribe_image(file_path, engine)


def claim_next_job():
    """Preleva atomicamente un job PENDING."""
    with db_connect() as conn:
        row = conn.execute("""
                           WITH candidate AS (
                               SELECT job_id
                               FROM ocr_jobs
                               WHERE status = 'PENDING'
                                 AND attempts < %s
                               ORDER BY created_at
                               FOR UPDATE SKIP LOCKED
                LIMIT 1
            )
                           UPDATE ocr_jobs AS job
                           SET status = 'PROCESSING',
                               attempts = job.attempts + 1,
                               started_at = NOW(),
                               updated_at = NOW(),
                               error = NULL
                               FROM candidate
                           WHERE job.job_id = candidate.job_id
                               RETURNING
                               job.job_id,
                               job.engine,
                               job.filenames,
                               job.filepaths,
                               job.attempts
                           """, (MAX_ATTEMPTS,)).fetchone()

        return row


def save_failure(job_id, error: str, attempts: int):
    with db_connect() as conn:
        conn.execute("""
                     UPDATE ocr_jobs
                     SET status = CASE
                                      WHEN attempts < %s THEN 'PENDING'
                                      ELSE 'FAILED'
                         END,
                         error = %s,
                         updated_at = NOW(),
                         completed_at = CASE
                                            WHEN attempts >= %s THEN NOW()
                                            ELSE NULL
                             END
                     WHERE job_id = %s
                       AND status = 'PROCESSING'
                     """, (MAX_ATTEMPTS, error, MAX_ATTEMPTS, job_id))


def process_claimed_job(job):
    job_id, engine, filenames, filepaths, attempts = job

    try:
        logger.info(
            "Avvio job %s: motore=%s, pagine=%s, tentativo=%s",
            job_id, engine, len(filepaths), attempts,
        )

        pages = []

        for index, file_path in enumerate(filepaths):
            path = Path(file_path)

            if not path.is_file():
                raise FileNotFoundError(
                    f"File della pagina non trovato: {path.name}"
                )

            text = recognize_image(str(path), engine)

            pages.append({
                "filename": filenames[index],
                "text": text,
            })

            logger.info(
                "Job %s: pagina %s/%s completata",
                job_id, index + 1, len(filepaths),
                        )

        combined_text = "\n\n".join(page["text"] for page in pages)

        # Risultati e stato COMPLETED vengono salvati nella stessa
        # transazione: non si può vedere COMPLETED senza il risultato.
        with db_connect() as conn:
            updated = conn.execute("""
                                   UPDATE ocr_jobs
                                   SET status = 'COMPLETED',
                                       result = %s,
                                       pages = %s,
                                       error = NULL,
                                       updated_at = NOW(),
                                       completed_at = NOW()
                                   WHERE job_id = %s
                                     AND status = 'PROCESSING'
                                   """, (
                                       combined_text,
                                       Jsonb(pages),
                                       job_id,
                                   )).rowcount

            if updated != 1:
                raise RuntimeError(
                    f"Impossibile completare il job {job_id}"
                )

        # Si eliminano i file solo dopo il commit del risultato.
        for file_path in filepaths:
            Path(file_path).unlink(missing_ok=True)

        logger.info("Job %s completato", job_id)

    except Exception as exc:
        logger.exception("Errore durante il job %s", job_id)

        try:
            save_failure(job_id, str(exc), attempts)
        except Exception:
            # Se anche PostgreSQL è irraggiungibile, il job può
            # restare PROCESSING e sarà recuperato al riavvio.
            logger.exception(
                "Impossibile salvare l'errore del job %s", job_id
            )


def worker_loop():
    logger.info("Worker OCR avviato")

    while not worker_stop.is_set():
        try:
            job = claim_next_job()

            if job is None:
                worker_stop.wait(POLL_INTERVAL)
                continue

            process_claimed_job(job)

        except Exception:
            logger.exception("Errore nel ciclo del worker OCR")
            worker_stop.wait(POLL_INTERVAL)

    logger.info("Worker OCR arrestato")


# --------------------------------------------------
# Avvio e arresto del servizio
# --------------------------------------------------

@asynccontextmanager
async def lifespan(app: FastAPI):
    global worker_thread

    init_database()
    recover_interrupted_jobs()

    worker_stop.clear()
    worker_thread = threading.Thread(
        target=worker_loop,
        name="ocr-worker",
        daemon=True,
    )
    worker_thread.start()

    try:
        yield
    finally:
        worker_stop.set()
        worker_thread.join(timeout=10)


app = FastAPI(lifespan=lifespan)


# --------------------------------------------------
# API
# --------------------------------------------------

@app.get("/")
def home():
    return {
        "message": "OCR Service attivo",
        "engines": sorted(SUPPORTED_ENGINES),
        "engine": "Tesseract OCR",
    }


@app.post("/ocr/batch")
def create_batch(
        files: list[UploadFile] = File(...),
        engine: str = Form(default="tesseract"),
):
    if engine not in SUPPORTED_ENGINES:
        raise HTTPException(
            status_code=400,
            detail=f"Motore OCR non supportato: {engine}",
        )

    if not files:
        raise HTTPException(
            status_code=400,
            detail="Nessun file ricevuto.",
        )

    # Validazione completa prima di salvare qualsiasi file.
    filenames = []
    extensions = []

    for index, file in enumerate(files, start=1):
        filename = Path(file.filename or f"page-{index}").name
        extension = Path(filename).suffix.lower()

        if extension not in ALLOWED_EXTENSIONS:
            raise HTTPException(
                status_code=400,
                detail=f"Formato non supportato: {extension}",
            )

        filenames.append(filename)
        extensions.append(extension)

    job_id = uuid4()
    file_paths = []

    try:
        for index, (file, extension) in enumerate(
                zip(files, extensions), start=1
        ):
            stored_filename = f"{job_id}_{index:04d}{extension}"
            file_path = UPLOAD_DIR / stored_filename

            with file_path.open("wb") as buffer:
                while chunk := file.file.read(1024 * 1024):
                    buffer.write(chunk)

            file_paths.append(str(file_path))

        with db_connect() as conn:
            conn.execute("""
                         INSERT INTO ocr_jobs (
                             job_id, status, engine, filenames,
                             filepaths, file_count
                         )
                         VALUES (%s, 'PENDING', %s, %s, %s, %s)
                         """, (
                             job_id,
                             engine,
                             Jsonb(filenames),
                             Jsonb(file_paths),
                             len(filenames),
                         ))

    except Exception as exc:
        for file_path in file_paths:
            Path(file_path).unlink(missing_ok=True)

        logger.exception("Impossibile creare il job OCR")
        raise HTTPException(
            status_code=500,
            detail="Impossibile accodare il job OCR.",
        ) from exc

    finally:
        for file in files:
            file.file.close()

    return {
        "jobId": str(job_id),
        "status": "PENDING",
        "filename": filenames[0],
        "fileCount": len(filenames),
    }


def parse_job_id(job_id: str) -> UUID:
    try:
        return UUID(job_id)
    except ValueError as exc:
        raise HTTPException(
            status_code=400,
            detail="Identificativo job non valido.",
        ) from exc


@app.get("/ocr/batch/{job_id}")
def get_batch_status(job_id: str):
    job_uuid = parse_job_id(job_id)

    with db_connect() as conn:
        job = conn.execute("""
                           SELECT job_id, status, filenames, file_count
                           FROM ocr_jobs
                           WHERE job_id = %s
                           """, (job_uuid,)).fetchone()

    if job is None:
        raise HTTPException(status_code=404, detail="Job non trovato")

    return {
        "jobId": str(job[0]),
        "status": job[1],
        "filename": job[2][0],
        "fileCount": job[3],
    }


@app.get("/ocr/batch/{job_id}/result")
def get_batch_result(job_id: str):
    job_uuid = parse_job_id(job_id)

    with db_connect() as conn:
        job = conn.execute("""
                           SELECT status, result, pages, error
                           FROM ocr_jobs
                           WHERE job_id = %s
                           """, (job_uuid,)).fetchone()

    if job is None:
        raise HTTPException(status_code=404, detail="Job non trovato")

    status, result, pages, error = job

    if status != "COMPLETED":
        return {
            "jobId": str(job_uuid),
            "status": status,
            "result": None,
            "error": error,
        }

    return {
        "jobId": str(job_uuid),
        "status": "COMPLETED",
        "result": result,
        "pages": pages or [],
    }
