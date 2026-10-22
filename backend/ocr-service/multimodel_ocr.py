
from argparse import Namespace
from pathlib import Path
import gc
import threading

_runtime = None
_active_engine = None
_model_lock = threading.Lock()


def _load_engine(engine: str):
    global _runtime, _active_engine

    import torch
    import model_gpu2 as ocr

    if engine not in ocr.MODELS:
        raise ValueError(f"Motore OCR non supportato: {engine}")

    if _active_engine == engine and _runtime is not None:  # Se il motore è già caricato
        return _runtime

    if _runtime is not None:
        del _runtime  #Rilascia il modello e il processore precedenti
        _runtime = None
        _active_engine = None
        gc.collect()

        if torch.cuda.is_available():
            torch.cuda.empty_cache()

    args = Namespace(
        model=engine,
        device="auto",
        dtype="auto",
        max_tokens=2048,
        max_pixels=1024 * 28 * 28,
        mode="transcribe",
        verbose=False,
        image=Path(".").resolve(),
    )

    device = ocr.get_device(args)
    dtype = ocr.get_dtype(args, device)
    model = ocr.load_model(args, device, dtype)
    processor = ocr.load_processor(args)

    _runtime = {
        "model": model,
        "processor": processor,
        "device": device,
        "dtype": dtype,
    }

    _active_engine = engine
    return _runtime

#Method checks if the engine is loaded and loads it if not, then transcribes the image using the specified engine.
#Prepare the image and run inference using the loaded model and processor, returning the transcribed text.
def transcribe_image(image_path: str, engine: str) -> str:
    with _model_lock:
        import model_gpu2 as ocr

        image = Path(image_path).resolve()

        if not image.is_file():
            raise FileNotFoundError(f"Immagine non trovata: {image}")

        runtime = _load_engine(engine)

        args = Namespace(
            model=engine,
            device="auto",
            dtype="auto",
            max_tokens=2048,
            max_pixels=1024 * 28 * 28,
            mode="transcribe",
            verbose=False,
            image=image,
        )

        inputs = ocr.prepare_inputs(
            args,
            runtime["processor"],
            runtime["device"],
            runtime["dtype"],
        )

        text = ocr.run_inference(
            args,
            runtime["model"],
            runtime["processor"],
            inputs,
            runtime["device"],
        )

        return text.strip()
