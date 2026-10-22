#!/usr/bin/env python3

import argparse
import sys
import time
from pathlib import Path

import torch

from transformers import (
    AutoProcessor,
    Qwen2_5_VLForConditionalGeneration,
    Qwen3_5ForConditionalGeneration,
    LightOnOcrForConditionalGeneration,
    LightOnOcrProcessor,
)

from qwen_vl_utils import process_vision_info


# ============================================================
# CONFIGURAZIONE
# ============================================================

MODELS = {
    "churro": "stanford-oval/churro-3B",
    "lighton-1b": "lightonai/LightOnOCR-3-1B",
    "lighton-4b": "lightonai/LightOnOCR-3-4B",
}

CHURRO_PROMPT = (
    "Transcribe the document exactly as written. "
    "Preserve line breaks, spelling, punctuation, "
    "capitalization, and abbreviations. "
    "Do not translate, summarize, correct, or explain "
    "the text. Output only the transcription."
)


# ============================================================
# ARGOMENTI
# ============================================================

def parse_args():

    parser = argparse.ArgumentParser(
        description="OCR multimodello con PyTorch e ROCm"
    )

    parser.add_argument(
        "image",
        type=Path,
        help="Immagine da trascrivere"
    )

    parser.add_argument(
        "--model",
        choices=MODELS.keys(),
        default="churro",
        help="Modello OCR"
    )

    parser.add_argument(
        "--device",
        choices=["auto", "cpu", "gpu"],
        default="auto",
        help="Dispositivo di inferenza"
    )

    parser.add_argument(
        "--dtype",
        choices=["auto", "float32", "float16", "bfloat16"],
        default="auto",
        help="Precisione del modello"
    )

    parser.add_argument(
        "--max-tokens",
        type=int,
        default=2048,
        help="Numero massimo di token generati"
    )

    parser.add_argument(
        "--max-pixels",
        type=int,
        default=1024 * 28 * 28,
        help="Limite pixel per Churro"
    )

    parser.add_argument(
        "--mode",
        choices=["transcribe", "grounding"],
        default="transcribe",
        help="Modalità LightOnOCR"
    )

    parser.add_argument(
        "--output",
        type=Path,
        default=None,
        help="Salva la trascrizione in TXT"
    )

    parser.add_argument(
        "--verbose",
        action="store_true",
        help="Mostra informazioni diagnostiche"
    )

    return parser.parse_args()


# ============================================================
# DISPOSITIVO
# ============================================================

def get_device(args):

    gpu_available = torch.cuda.is_available()

    if args.device == "gpu":

        if not gpu_available:
            raise RuntimeError(
                "GPU richiesta ma non disponibile in PyTorch. "
                "Verifica ROCm e la compatibilità della GPU."
            )

        return torch.device("cuda:0")

    if args.device == "cpu":
        return torch.device("cpu")

    return torch.device(
        "cuda:0" if gpu_available else "cpu"
    )


# ============================================================
# PRECISIONE
# ============================================================

def get_dtype(args, device):

    dtypes = {
        "float32": torch.float32,
        "float16": torch.float16,
        "bfloat16": torch.bfloat16,
    }

    if args.dtype != "auto":
        return dtypes[args.dtype]

    if device.type == "cpu":
        return torch.float32

    if args.model.startswith("lighton"):
        return torch.bfloat16

    return torch.float16


# ============================================================
# INFORMAZIONI SISTEMA
# ============================================================

def print_system_info(args, device, dtype):

    print("=" * 60)
    print("MULTI MODEL OCR")
    print("=" * 60)

    print(f"Modello: {args.model}")
    print(f"Repository: {MODELS[args.model]}")
    print(f"Immagine: {args.image}")

    print()
    print(f"PyTorch: {torch.__version__}")
    print(f"ROCm: {torch.version.hip}")
    print(f"Dispositivo: {device}")
    print(f"Precisione: {dtype}")

    if device.type == "cuda":

        print(
            "GPU:",
            torch.cuda.get_device_name(0)
        )

        total_memory = (
                torch.cuda.get_device_properties(0).total_memory
                / 1024**3
        )

        print(f"Memoria GPU: {total_memory:.2f} GB")

    print("=" * 60)


# ============================================================
# CARICAMENTO MODELLO
# ============================================================

def load_model(args, device, dtype):

    model_id = MODELS[args.model]

    print()
    print(f"Caricamento {model_id}...")

    start = time.perf_counter()

    if args.model == "churro":

        model_class = Qwen2_5_VLForConditionalGeneration

    elif args.model == "lighton-1b":

        model_class = LightOnOcrForConditionalGeneration

    elif args.model == "lighton-4b":

        model_class = Qwen3_5ForConditionalGeneration

    else:

        raise ValueError(
            f"Modello non supportato: {args.model}"
        )

    model = model_class.from_pretrained(
        model_id,
        dtype=dtype,
        low_cpu_mem_usage=True,
    )

    model = model.to(device)
    model.eval()

    elapsed = time.perf_counter() - start

    print(f"Modello caricato in {elapsed:.2f} secondi")
    print("Dispositivo modello:", model.device)

    if args.verbose:

        first_param = next(model.parameters())

        print(
            "Primo parametro:",
            first_param.device,
            first_param.dtype
        )

    return model


# ============================================================
# CARICAMENTO PROCESSOR
# ============================================================

def load_processor(args):

    model_id = MODELS[args.model]

    print()
    print("Caricamento processor...")

    if args.model == "churro":

        return AutoProcessor.from_pretrained(
            model_id,
            min_pixels=256 * 28 * 28,
            max_pixels=args.max_pixels,
        )

    if args.model == "lighton-1b":

        return LightOnOcrProcessor.from_pretrained(
            model_id
        )

    return AutoProcessor.from_pretrained(
        model_id
    )


# ============================================================
# PREPARAZIONE INPUT
# ============================================================

def prepare_inputs(args, processor, device, dtype):

    print()
    print("Preparazione immagine...")

    # --------------------------------------------------------
    # CHURRO
    # --------------------------------------------------------

    if args.model == "churro":

        messages = [
            {
                "role": "user",
                "content": [
                    {
                        "type": "image",
                        "image": args.image.as_uri(),
                    },
                    {
                        "type": "text",
                        "text": CHURRO_PROMPT,
                    },
                ],
            }
        ]

        text = processor.apply_chat_template(
            messages,
            tokenize=False,
            add_generation_prompt=True,
        )

        image_inputs, video_inputs = process_vision_info(
            messages
        )

        inputs = processor(
            text=[text],
            images=image_inputs,
            videos=video_inputs,
            padding=True,
            return_tensors="pt",
        )

    # --------------------------------------------------------
    # LIGHTONOCR
    # --------------------------------------------------------

    else:

        content = [
            {
                "type": "image",
                "url": str(args.image),
            }
        ]

        # LightOnOCR:
        # prompt vuoto = trascrizione
        # "grounding" = testo + coordinate

        if args.mode == "grounding":

            content.append({
                "type": "text",
                "text": "grounding",
            })

        messages = [
            {
                "role": "user",
                "content": content,
            }
        ]

        template_kwargs = {
            "add_generation_prompt": True,
            "tokenize": True,
            "return_dict": True,
            "return_tensors": "pt",
        }

        # Qwen3.5: disabilita thinking
        if args.model == "lighton-4b":

            template_kwargs["enable_thinking"] = False

        inputs = processor.apply_chat_template(
            messages,
            **template_kwargs
        )

    # --------------------------------------------------------
    # TRASFERIMENTO SUL DISPOSITIVO
    # --------------------------------------------------------

    # Trasferisce ogni tensore sulla GPU/CPU.
    # Converte soltanto i tensori floating point.
    # I tensori interi devono conservare il loro dtype.

    for key, value in inputs.items():

        if not isinstance(value, torch.Tensor):
            continue

        if value.is_floating_point():

            inputs[key] = value.to(
                device=device,
                dtype=dtype
            )

        else:

            inputs[key] = value.to(
                device=device
            )

    if args.verbose:

        print()
        print("Diagnostica input:")

        for key, value in inputs.items():

            if isinstance(value, torch.Tensor):

                print(
                    f"{key}: "
                    f"device={value.device}, "
                    f"dtype={value.dtype}, "
                    f"shape={tuple(value.shape)}"
                )

    return inputs


# ============================================================
# INFERENZA
# ============================================================

def run_inference(
        args,
        model,
        processor,
        inputs,
        device,
):

    print()
    print("=" * 60)
    print("INFERENZA OCR")
    print("=" * 60)

    if device.type == "cuda":

        torch.cuda.reset_peak_memory_stats(device)
        torch.cuda.synchronize(device)

    start = time.perf_counter()

    generation_kwargs = {
        "max_new_tokens": args.max_tokens,
        "use_cache": True,
    }

    if args.model == "lighton-4b":

        # Impostazioni dell'esempio ufficiale LightOnOCR-3-4B
        generation_kwargs.update({
            "do_sample": True,
            "temperature": 0.2,
        })

    else:

        generation_kwargs["do_sample"] = False

    with torch.inference_mode():

        generated_ids = model.generate(
            **inputs,
            **generation_kwargs
        )

    if device.type == "cuda":
        torch.cuda.synchronize(device)

    elapsed = time.perf_counter() - start

    # --------------------------------------------------------
    # RIMOZIONE TOKEN PROMPT
    # --------------------------------------------------------

    prompt_length = inputs["input_ids"].shape[1]

    generated_ids_trimmed = generated_ids[
        :,
        prompt_length:
    ]

    # --------------------------------------------------------
    # DECODIFICA
    # --------------------------------------------------------

    output_text = processor.batch_decode(
        generated_ids_trimmed,
        skip_special_tokens=True,
        clean_up_tokenization_spaces=False,
    )[0]

    # --------------------------------------------------------
    # STATISTICHE
    # --------------------------------------------------------

    num_tokens = generated_ids_trimmed.shape[1]

    print()
    print(f"Tempo inferenza: {elapsed:.2f} secondi")
    print(f"Token generati: {num_tokens}")

    if elapsed > 0:

        print(
            f"Velocità media: "
            f"{num_tokens / elapsed:.2f} token/s"
        )

    if device.type == "cuda":

        peak = (
                torch.cuda.max_memory_allocated(device)
                / 1024**3
        )

        print(f"Picco memoria GPU: {peak:.2f} GB")

    return output_text


# ============================================================
# MAIN
# ============================================================

def main():

    args = parse_args()

    args.image = (
        args.image.expanduser().resolve()
    )

    if not args.image.is_file():

        print(
            f"File non trovato: {args.image}",
            file=sys.stderr
        )

        sys.exit(1)

    if args.max_tokens <= 0:
        sys.exit("Errore: --max-tokens deve essere positivo")

    if args.max_pixels < 256 * 28 * 28:
        sys.exit("Errore: --max-pixels troppo piccolo")

    if args.model == "churro" and args.mode == "grounding":
        sys.exit(
            "La modalità grounding è disponibile "
            "solo per LightOnOCR."
        )

    try:

        # ----------------------------------------------------
        # DISPOSITIVO
        # ----------------------------------------------------

        device = get_device(args)

        dtype = get_dtype(args, device)

        print_system_info(
            args,
            device,
            dtype
        )

        # ----------------------------------------------------
        # MODELLO
        # ----------------------------------------------------

        model = load_model(
            args,
            device,
            dtype
        )

        # ----------------------------------------------------
        # PROCESSOR
        # ----------------------------------------------------

        processor = load_processor(args)

        # ----------------------------------------------------
        # INPUT
        # ----------------------------------------------------

        inputs = prepare_inputs(
            args,
            processor,
            device,
            dtype
        )

        # ----------------------------------------------------
        # INFERENZA
        # ----------------------------------------------------

        output_text = run_inference(
            args,
            model,
            processor,
            inputs,
            device
        )

        # ----------------------------------------------------
        # RISULTATO
        # ----------------------------------------------------

        print()
        print("=" * 60)
        print("TRASCRIZIONE")
        print("=" * 60)
        print()

        print(output_text)

        # ----------------------------------------------------
        # SALVATAGGIO
        # ----------------------------------------------------

        if args.output is not None:

            output_path = (
                args.output.expanduser().resolve()
            )

            output_path.parent.mkdir(
                parents=True,
                exist_ok=True
            )

            output_path.write_text(
                output_text,
                encoding="utf-8"
            )

            print()
            print(
                f"Trascrizione salvata in: {output_path}"
            )

        print()
        print("=" * 60)
        print("OPERAZIONE COMPLETATA")
        print("=" * 60)

    except KeyboardInterrupt:

        print("\nOperazione interrotta.")
        sys.exit(130)

    except torch.OutOfMemoryError:

        print(
            "\nERRORE: memoria insufficiente.\n"
            "Prova un modello più piccolo oppure "
            "riduci la risoluzione dell'immagine.",
            file=sys.stderr
        )

        sys.exit(1)

    except Exception as error:

        print()
        print("=" * 60)
        print("ERRORE")
        print("=" * 60)

        print(error, file=sys.stderr)

        if args.verbose:

            import traceback
            traceback.print_exc()

        sys.exit(1)


if __name__ == "__main__":
    main()
