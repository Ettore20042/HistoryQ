from fastapi import FastAPI, BackgroundTasks, UploadFile, File, Form, HTTPException
from uuid import uuid4
from pathlib import Path
from urllib.parse import unquote
import os
import traceback
from PIL import Image
import pytesseract
from fastapi import Depends
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
import secrets

app = FastAPI()
# Security scheme for token-based authentication
security = HTTPBearer(auto_error=False)
OCR_API_TOKEN = os.getenv("OCR_API_TOKEN")


def verify_token(
        credentials: HTTPAuthorizationCredentials = Depends(security),
):
    if not OCR_API_TOKEN:
        raise HTTPException(
            status_code=500,
            detail="OCR_API_TOKEN non configurato sul server.",
        )

    if (
            credentials is None
            or not secrets.compare_digest(
        credentials.credentials,
        OCR_API_TOKEN,
    )
    ):
        raise HTTPException(
            status_code=401,
            detail="Token di autenticazione mancante o non valido.",
        )

# 1. Determinazione del percorso assoluto per il controllo
_initial_path_str = str(Path(os.getenv("OCR_UPLOAD_DIR", "uploads")).resolve())

# 2. Il booleano viene settato a True se il percorso contiene '@' o '%40'
NEED_PATH_FIX: bool = ("@" in _initial_path_str) or ("%40" in _initial_path_str)

# 3. Impostazione di UPLOAD_DIR in base al booleano
if NEED_PATH_FIX:
    UPLOAD_DIR = Path(unquote(_initial_path_str))
else:
    UPLOAD_DIR = Path(os.getenv("OCR_UPLOAD_DIR", "uploads"))

UPLOAD_DIR.mkdir(exist_ok=True, parents=True)

# 4. MONKEY PATCH: Intercetta PIL.Image.open per correggere i percorsi con %40 generati da qwen_vl_utils
if NEED_PATH_FIX:
    _orig_image_open = Image.open

    def _patched_image_open(fp, *args, **kwargs):
        if isinstance(fp, (str, bytes)):
            fp = unquote(str(fp))
        elif isinstance(fp, Path):
            fp = Path(unquote(str(fp)))
        return _orig_image_open(fp, *args, **kwargs)

    Image.open = _patched_image_open

TESSERACT_PATH = os.getenv("TESSERACT_PATH")
if TESSERACT_PATH:
    pytesseract.pytesseract.tesseract_cmd = unquote(TESSERACT_PATH) if NEED_PATH_FIX else TESSERACT_PATH

OCR_LANGUAGE = os.getenv("OCR_LANGUAGE", "ita")
TESSERACT_CONFIG = os.getenv("TESSERACT_CONFIG", "--psm 3")

ALLOWED_EXTENSIONS = {
    ".jpg", ".jpeg", ".png", ".tiff", ".webp"
}
SUPPORTED_ENGINES = {"tesseract", "churro", "lighton-1b", "lighton-4b"}

jobs = {}


@app.get("/")
def home():
    return {
        "message": "OCR Service attivo",
        "engine": "Tesseract OCR",
        "path_fix_active": NEED_PATH_FIX
    }


def process_job(job_id: str):
    jobs[job_id]["status"] = "PROCESSING"

    file_paths = jobs[job_id]["filepaths"]

    try:
        print(f"[OCR] Avvio job: {job_id}")
        print(f"[OCR] Numero pagine: {len(file_paths)}")

        results = []
        pages = []

        for index, raw_file_path in enumerate(file_paths, start=1):
            file_path = unquote(raw_file_path) if NEED_PATH_FIX else raw_file_path

            print(
                f"[OCR] Elaborazione pagina {index}/{len(file_paths)}: "
                f"raw='{raw_file_path}' | target='{file_path}' | exists={os.path.exists(file_path)}"
            )

            image = Image.open(file_path)

            print(
                f"[OCR] Immagine caricata: "
                f"{image.width}x{image.height}"
            )

            if image.mode != "RGB":
                image = image.convert("RGB")

            engine = jobs[job_id]["engine"]
            if engine == "tesseract":
                text = pytesseract.image_to_string(
                    image,
                    lang=OCR_LANGUAGE,
                    config=TESSERACT_CONFIG
                )
            else:
                from multimodel_ocr import transcribe_image

                text = transcribe_image(file_path, engine)

            results.append(text)
            pages.append({
                "filename": jobs[job_id]["filenames"][index - 1],
                "text": text
            })

            print(f"[OCR] Pagina {index} completata")

        combined_text = "\n\n".join(results)

        print("[OCR] OCR completo terminato")

        jobs[job_id]["status"] = "COMPLETED"
        jobs[job_id]["result"] = combined_text
        jobs[job_id]["pages"] = pages

    except Exception as e:
        print("\n" + "!" * 50)
        print(f"[OCR] ERRORE RILEVATO: {e}")
        print(f"[OCR DIAGNOSTICA] NEED_PATH_FIX: {NEED_PATH_FIX}")
        print(f"[OCR DIAGNOSTICA] Current Working Dir (os.getcwd()): {os.getcwd()}")
        print(f"[OCR DIAGNOSTICA] Filepaths ricevuti nel job: {file_paths}")

        for idx, fp in enumerate(file_paths, start=1):
            target_fp = unquote(fp) if NEED_PATH_FIX else fp
            print(f"[OCR DIAGNOSTICA] File #{idx}:")
            print(f"  - Stringa originale: {fp}")
            print(f"  - Stringa utilizzata: {target_fp}")
            print(f"  - Esiste su disco? {os.path.exists(target_fp)}")

        print("\n[OCR DIAGNOSTICA] TRACEBACK COMPLETO:")
        traceback.print_exc()
        print("!" * 50 + "\n")

        jobs[job_id]["status"] = "FAILED"
        jobs[job_id]["result"] = None
        jobs[job_id]["error"] = str(e)
    finally:
        for raw_file_path in file_paths:
            target_path = Path(unquote(raw_file_path)) if NEED_PATH_FIX else Path(raw_file_path)
            target_path.unlink(missing_ok=True)


@app.post("/ocr/batch")
def create_batch(
        background_tasks: BackgroundTasks,
        engine: str = Form("tesseract"),
        files: list[UploadFile] = File(...),
        _:None = Depends(verify_token)
):
    if not files:
        raise HTTPException(
            status_code=400,
            detail="Nessun file ricevuto."
        )

    if engine not in SUPPORTED_ENGINES:
        raise HTTPException(
            status_code=400,
            detail=f"Motore OCR non supportato: {engine}"
        )

    job_id = str(uuid4())

    file_paths = []
    filenames = []

    for index, file in enumerate(files, start=1):

        filename = file.filename or f"page-{index}"
        extension = Path(filename).suffix.lower()

        if extension not in ALLOWED_EXTENSIONS:
            raise HTTPException(
                status_code=400,
                detail=(
                        "Formato non supportato: "
                        + extension
                )
            )

        stored_filename = (
            f"{job_id}_{index:04d}{extension}"
        )

        file_path = UPLOAD_DIR / stored_filename

        if NEED_PATH_FIX:
            file_path = Path(unquote(str(file_path.resolve())))

        with open(file_path, "wb") as buffer:
            buffer.write(file.file.read())

        file_paths.append(str(file_path))
        filenames.append(filename)

    jobs[job_id] = {
        "status": "PENDING",
        "engine": engine,
        "filenames": filenames,
        "filepaths": file_paths,
        "result": None,
        "pages": [],
        "error": None
    }

    background_tasks.add_task(
        process_job,
        job_id
    )

    return {
        "jobId": job_id,
        "status": "PENDING",
        "filename": filenames[0],
        "fileCount": len(filenames)
    }


@app.get("/ocr/batch/{job_id}")
def get_batch_status(job_id: str, _:None = Depends(verify_token)):

    if job_id not in jobs:
        raise HTTPException(
            status_code=404,
            detail="Job non trovato"
        )

    return {
        "jobId": job_id,
        "status": jobs[job_id]["status"],
        "filename": jobs[job_id]["filenames"][0],
        "fileCount": len(jobs[job_id]["filenames"])
    }


@app.get("/ocr/batch/{job_id}/result")
def get_batch_result(job_id: str, _:None = Depends(verify_token)):

    if job_id not in jobs:
        raise HTTPException(
            status_code=404,
            detail="Job non trovato"
        )

    job = jobs[job_id]

    if job["status"] != "COMPLETED":
        return {
            "jobId": job_id,
            "status": job["status"],
            "result": None,
            "error": job.get("error")
        }

    return {
        "jobId": job_id,
        "status": "COMPLETED",
        "result": job["result"],
        "pages": job.get("pages", [])
    }
