from fastapi import FastAPI, BackgroundTasks
from uuid import uuid4
import time

app = FastAPI()

jobs = {}


@app.get("/")
def home():
    return {"message": "OCR Service attivo"}


def process_job(job_id: str):
    jobs[job_id]["status"] = "PROCESSING"

    time.sleep(10)

    jobs[job_id]["status"] = "COMPLETED"
    jobs[job_id]["result"] = "Risultato OCR simulato"


@app.post("/ocr/batch")
def create_batch(background_tasks: BackgroundTasks):
    job_id = str(uuid4())

    jobs[job_id] = {
        "status": "PENDING"
    }

    background_tasks.add_task(process_job, job_id)

    return {
        "jobId": job_id,
        "status": "PENDING"
    }
@app.get("/ocr/batch/{job_id}")
def get_batch_status(job_id: str):
    if job_id not in jobs:
        return {
            "error": "Job non trovato"
        }

    return {
        "jobId": job_id,
        "status": jobs[job_id]["status"]
    }
@app.get("/ocr/batch/{job_id}/result")
def get_batch_result(job_id: str):
    if job_id not in jobs:
        return {
            "error": "Job non trovato"
        }

    if jobs[job_id]["status"] != "COMPLETED":
        return {
            "jobId": job_id,
            "status": jobs[job_id]["status"],
            "result": None
        }

    return {
        "jobId": job_id,
        "status": "COMPLETED",
        "result": jobs[job_id]["result"]
    }