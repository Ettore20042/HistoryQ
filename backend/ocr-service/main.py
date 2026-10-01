from fastapi import FastAPI, BackgroundTasks, UploadFile, File, HTTPException
from uuid import uuid4
from pathlib import Path
from PIL import Image
import pytesseract


app = FastAPI()


# ============================================================
# CONFIGURAZIONE
# ============================================================

UPLOAD_DIR = Path("uploads")
UPLOAD_DIR.mkdir(exist_ok=True)

# Percorso di Tesseract su Windows
TESSERACT_PATH = r"C:\Program Files\Tesseract-OCR\tesseract.exe"

# Configura pytesseract per utilizzare Tesseract
pytesseract.pytesseract.tesseract_cmd = TESSERACT_PATH

# Lingua OCR
OCR_LANGUAGE = "ita"

# Configurazione Tesseract
# PSM 3 = segmentazione automatica della pagina
TESSERACT_CONFIG = "--psm 3"

# Formati immagine accettati
ALLOWED_EXTENSIONS = {
    ".jpg",
    ".jpeg",
    ".png",
    ".tiff",
    ".webp"
}


# ============================================================
# JOB IN MEMORIA
# ============================================================

jobs = {}


# ============================================================
# HOME
# ============================================================

@app.get("/")
def home():
    return {
        "message": "OCR Service attivo",
        "engine": "Tesseract OCR"
    }


# ============================================================
# PROCESSAMENTO OCR
# ============================================================

def process_job(job_id: str):

    jobs[job_id]["status"] = "PROCESSING"

    file_path = jobs[job_id]["filepath"]

    try:

        print(f"[OCR] Avvio job: {job_id}")
        print(f"[OCR] File: {file_path}")

        # Apertura immagine
        image = Image.open(file_path)

        print(
            f"[OCR] Immagine caricata: "
            f"{image.width}x{image.height}"
        )

        # Conversione in RGB
        if image.mode != "RGB":
            image = image.convert("RGB")

        # Esecuzione OCR tramite Tesseract
        text = pytesseract.image_to_string(
            image,
            lang=OCR_LANGUAGE,
            config=TESSERACT_CONFIG
        )

        print("[OCR] OCR completato")

        # Salvataggio risultato
        jobs[job_id]["status"] = "COMPLETED"
        jobs[job_id]["result"] = text

    except Exception as e:

        print(f"[OCR] Errore: {e}")

        jobs[job_id]["status"] = "FAILED"
        jobs[job_id]["result"] = None
        jobs[job_id]["error"] = str(e)


# ============================================================
# CREAZIONE JOB
# ============================================================

@app.post("/ocr/batch")
def create_batch(
        background_tasks: BackgroundTasks,
        file: UploadFile = File(...)
):

    filename = file.filename or "image"

    extension = Path(filename).suffix.lower()

    # Controllo formato
    if extension not in ALLOWED_EXTENSIONS:

        raise HTTPException(
            status_code=400,
            detail=(
                "Formato non supportato. "
                "Usa JPG, JPEG, PNG, TIFF o WEBP."
            )
        )

    # Generazione ID del job
    job_id = str(uuid4())

    # Nome file interno
    stored_filename = f"{job_id}{extension}"

    file_path = UPLOAD_DIR / stored_filename

    # Salvataggio file
    with open(file_path, "wb") as buffer:
        buffer.write(file.file.read())

    # Creazione job
    jobs[job_id] = {
        "status": "PENDING",
        "filename": filename,
        "filepath": str(file_path),
        "result": None,
        "error": None
    }

    # Avvio OCR in background
    background_tasks.add_task(
        process_job,
        job_id
    )

    return {
        "jobId": job_id,
        "status": "PENDING",
        "filename": filename
    }


# ============================================================
# STATO JOB
# ============================================================

@app.get("/ocr/batch/{job_id}")
def get_batch_status(job_id: str):

    if job_id not in jobs:

        raise HTTPException(
            status_code=404,
            detail="Job non trovato"
        )

    return {
        "jobId": job_id,
        "status": jobs[job_id]["status"],
        "filename": jobs[job_id]["filename"]
    }


# ============================================================
# RISULTATO OCR
# ============================================================

@app.get("/ocr/batch/{job_id}/result")
def get_batch_result(job_id: str):

    if job_id not in jobs:

        raise HTTPException(
            status_code=404,
            detail="Job non trovato"
        )

    job = jobs[job_id]

    # OCR non ancora terminato
    if job["status"] != "COMPLETED":

        return {
            "jobId": job_id,
            "status": job["status"],
            "result": None,
            "error": job.get("error")
        }

    # OCR completato
    return {
        "jobId": job_id,
        "status": "COMPLETED",
        "result": job["result"]
    }