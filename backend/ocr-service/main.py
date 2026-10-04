
from fastapi import FastAPI, BackgroundTasks, UploadFile, File, HTTPException
from uuid import uuid4
from pathlib import Path
from PIL import Image
import pytesseract

app = FastAPI()

UPLOAD_DIR = Path("uploads")
UPLOAD_DIR.mkdir(exist_ok=True)

TESSERACT_PATH = r"C:\Program Files\Tesseract-OCR\tesseract.exe"
pytesseract.pytesseract.tesseract_cmd = TESSERACT_PATH

OCR_LANGUAGE = "ita"
TESSERACT_CONFIG = "--psm 3"

ALLOWED_EXTENSIONS = {
    ".jpg", ".jpeg", ".png", ".tiff", ".webp"
}

jobs = {}


@app.get("/")
def home():
    return {
        "message": "OCR Service attivo",
        "engine": "Tesseract OCR"
    }


def process_job(job_id: str):
    jobs[job_id]["status"] = "PROCESSING"

    file_paths = jobs[job_id]["filepaths"]

    try:
        print(f"[OCR] Avvio job: {job_id}")
        print(f"[OCR] Numero pagine: {len(file_paths)}")

        results = []

        for index, file_path in enumerate(file_paths, start=1):
            print(
                f"[OCR] Elaborazione pagina {index}/{len(file_paths)}: "
                f"{file_path}"
            )

            image = Image.open(file_path)

            print(
                f"[OCR] Immagine caricata: "
                f"{image.width}x{image.height}"
            )

            if image.mode != "RGB":
                image = image.convert("RGB")

            text = pytesseract.image_to_string(
                image,
                lang=OCR_LANGUAGE,
                config=TESSERACT_CONFIG
            )

            results.append(text)

            print(f"[OCR] Pagina {index} completata")

        combined_text = "\n\n".join(results)

        print("[OCR] OCR completo terminato")

        jobs[job_id]["status"] = "COMPLETED"
        jobs[job_id]["result"] = combined_text

    except Exception as e:
        print(f"[OCR] Errore: {e}")

        jobs[job_id]["status"] = "FAILED"
        jobs[job_id]["result"] = None
        jobs[job_id]["error"] = str(e)


@app.post("/ocr/batch")
def create_batch(
        background_tasks: BackgroundTasks,
        files: list[UploadFile] = File(...)
):
    if not files:
        raise HTTPException(
            status_code=400,
            detail="Nessun file ricevuto."
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

        with open(file_path, "wb") as buffer:
            buffer.write(file.file.read())

        file_paths.append(str(file_path))
        filenames.append(filename)

    jobs[job_id] = {
        "status": "PENDING",
        "filenames": filenames,
        "filepaths": file_paths,
        "result": None,
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
def get_batch_status(job_id: str):

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
def get_batch_result(job_id: str):

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
        "result": job["result"]
    }

