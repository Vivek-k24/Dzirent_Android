"""Precomputes the label text embeddings the app compares photos with.

    python make_text_embeddings.py --tinyclip PATH/TO/TinyCLIP --checkpoint PATH/TO/ckpt.pt

For each label in labels.py, every template is filled with the label's phrase, encoded with
TinyCLIP's text encoder, normalized and averaged (CLIP prompt ensembling), then normalized
again. The text encoder never ships in the app; only this small JSON does.

Output: android/app/src/main/assets/ml/label_embeddings.json
"""

import argparse
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(os.path.dirname(HERE))
DEFAULT_OUT = os.path.join(REPO, "android", "app", "src", "main", "assets", "ml", "label_embeddings.json")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--tinyclip", required=True, help="local clone of github.com/wkcn/TinyCLIP")
    parser.add_argument("--checkpoint", required=True, help="TinyCLIP-ResNet-19M-Text-19M-LAION400M.pt")
    parser.add_argument("--out", default=DEFAULT_OUT)
    args = parser.parse_args()

    import torch

    sys.path.insert(0, HERE)
    import tinyclip
    from labels import LABELS, TEMPLATES

    model, open_clip = tinyclip.load(args.tinyclip, args.checkpoint)
    embeddings = []
    with torch.no_grad():
        for name, phrase in LABELS:
            tokens = open_clip.tokenize([t.format(phrase) for t in TEMPLATES])
            e = model.encode_text(tokens)
            e = e / e.norm(dim=-1, keepdim=True)
            mean = e.mean(dim=0)
            embeddings.append((mean / mean.norm()).tolist())
        logit_scale = float(model.logit_scale.exp())

    data = {
        "model": tinyclip.ARCH,
        "checkpoint": tinyclip.CHECKPOINT,
        "logit_scale": round(logit_scale, 3),
        "templates": TEMPLATES,
        "dim": len(embeddings[0]),
        "labels": [name for name, _ in LABELS],
        "embeddings": [[round(x, 5) for x in e] for e in embeddings],
    }
    os.makedirs(os.path.dirname(args.out), exist_ok=True)
    with open(args.out, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, separators=(",", ":"))
        f.write("\n")
    print(f"{len(LABELS)} labels, dim {data['dim']}, logit scale {logit_scale:.1f} -> {args.out}")


if __name__ == "__main__":
    main()
