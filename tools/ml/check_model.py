"""Runs the app's exported model on your own photos and prints the top 3 labels.

    python check_model.py photo1.jpg photo2.jpg ...

Uses the same files the app ships (assets/ml/tinyclip_image.tflite and label_embeddings.json)
and the same preprocessing: upright (EXIF), centered square crop, 224 x 224, CLIP
normalization. Runs on your PC; no emulator, no network. For each photo it also says what the
app would show (a suggestion, a policy warning or nothing), and with several photos, how
similar each one is to the first (the "looks like a different item" check).
"""

import argparse
import json
import os
import sys

import numpy as np
from PIL import Image, ImageOps

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(os.path.dirname(HERE))
ASSETS = os.path.join(REPO, "android", "app", "src", "main", "assets", "ml")
MEAN = np.array([0.48145466, 0.4578275, 0.40821073], np.float32)
STD = np.array([0.26862954, 0.26130258, 0.27577711], np.float32)


def preprocess(path: str) -> np.ndarray:
    """Returns a (224, 224, 3) normalized float array, like ClipInput in the app."""
    img = ImageOps.exif_transpose(Image.open(path)).convert("RGB")
    img.thumbnail((1280, 1280), Image.BILINEAR)  # the app's working size
    side = min(img.size)
    left, top = (img.width - side) // 2, (img.height - side) // 2
    img = img.crop((left, top, left + side, top + side)).resize((224, 224), Image.BILINEAR, reducing_gap=2.0)
    return (np.asarray(img, np.float32) / 255.0 - MEAN) / STD


def load_interpreter(model_path: str):
    try:
        from ai_edge_litert.interpreter import Interpreter
    except ImportError:
        from tensorflow.lite import Interpreter  # type: ignore
    interpreter = Interpreter(model_path=model_path)
    interpreter.allocate_tensors()
    return interpreter


def embed(interpreter, x: np.ndarray) -> np.ndarray:
    inp = interpreter.get_input_details()[0]
    out = interpreter.get_output_details()[0]
    batch = x[None]
    if list(inp["shape"]) == [1, 3, 224, 224]:
        batch = np.transpose(batch, (0, 3, 1, 2))
    interpreter.set_tensor(inp["index"], batch.astype(np.float32))
    interpreter.invoke()
    e = interpreter.get_tensor(out["index"])[0].astype(np.float64)
    return e / np.linalg.norm(e)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("images", nargs="+")
    parser.add_argument("--model", default=os.path.join(ASSETS, "tinyclip_image.tflite"))
    parser.add_argument("--labels", default=os.path.join(ASSETS, "label_embeddings.json"))
    args = parser.parse_args()

    sys.path.insert(0, HERE)
    from labels import LOGIT_SCALE, POLICY_CONFIDENCE, POLICY_GROUPS, SAME_ITEM_MIN_SIMILARITY, SUGGEST_CONFIDENCE

    with open(args.labels, encoding="utf-8") as f:
        data = json.load(f)
    names = data["labels"]
    text = np.array(data["embeddings"], np.float64)
    interpreter = load_interpreter(args.model)

    first = None
    for path in args.images:
        e = embed(interpreter, preprocess(path))
        logits = LOGIT_SCALE * text @ e
        p = np.exp(logits - logits.max())
        p /= p.sum()
        order = np.argsort(-p)
        print(f"\n{os.path.basename(path)}")
        for rank, i in enumerate(order[:3], 1):
            print(f"  {rank}. {names[i]:<16} {p[i] * 100:5.1f}%")

        groups: dict[str, float] = {}
        for i, name in enumerate(names):
            if name in POLICY_GROUPS:
                groups[POLICY_GROUPS[name]] = groups.get(POLICY_GROUPS[name], 0.0) + p[i]
        group, total = max(groups.items(), key=lambda kv: kv[1])
        top = names[order[0]]
        if total >= POLICY_CONFIDENCE:
            print(f"  app: policy warning ({group}, {total * 100:.0f}%)")
        elif top not in POLICY_GROUPS and p[order[0]] >= SUGGEST_CONFIDENCE:
            print(f"  app: suggests '{top}'")
        else:
            print("  app: shows nothing (not confident enough)")

        if first is None:
            first = e
        else:
            similarity = float(first @ e)
            verdict = "same item" if similarity >= SAME_ITEM_MIN_SIMILARITY else "looks like a different item"
            print(f"  similarity to the first photo: {similarity:.3f} ({verdict})")


if __name__ == "__main__":
    main()
