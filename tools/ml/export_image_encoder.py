"""Exports TinyCLIP's image encoder to a quantized TFLite (LiteRT) model for the app.

    python export_image_encoder.py --tinyclip PATH/TO/TinyCLIP --checkpoint PATH/TO/ckpt.pt

Steps: PyTorch -> ONNX (opset 17) -> onnx2tf (TensorFlow SavedModel, NHWC input) -> TFLite with
dynamic-range quantization (int8 weights, float activations). Only the image encoder ships;
the text encoder stays here (see make_text_embeddings.py). The script finishes by checking
that the TFLite embeddings match PyTorch's on a few test images (cosine similarity > 0.98).

Output: android/app/src/main/assets/ml/tinyclip_image.tflite
"""

import argparse
import os
import shutil
import subprocess
import sys
import tempfile

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(os.path.dirname(HERE))
DEFAULT_OUT = os.path.join(REPO, "android", "app", "src", "main", "assets", "ml", "tinyclip_image.tflite")
MEAN = np.array([0.48145466, 0.4578275, 0.40821073], np.float32)
STD = np.array([0.26862954, 0.26130258, 0.27577711], np.float32)


def test_inputs(n: int = 4) -> np.ndarray:
    """Deterministic, image-like inputs (smooth color fields with edges), NCHW, normalized."""
    rng = np.random.default_rng(0)
    yy, xx = np.mgrid[0:224, 0:224] / 224.0
    images = []
    for _ in range(n):
        base = rng.uniform(0, 1, 3)
        tilt = rng.uniform(-0.5, 0.5, (3, 2))
        img = base[:, None, None] + tilt[:, 0, None, None] * xx + tilt[:, 1, None, None] * yy
        x0, y0 = rng.integers(20, 120, 2)
        img[:, y0:y0 + 80, x0:x0 + 60] = rng.uniform(0, 1, 3)[:, None, None]
        img = np.clip(img, 0, 1).astype(np.float32)
        images.append((img - MEAN[:, None, None]) / STD[:, None, None])
    return np.stack(images).astype(np.float32)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--tinyclip", required=True, help="local clone of github.com/wkcn/TinyCLIP")
    parser.add_argument("--checkpoint", required=True, help="TinyCLIP-ResNet-19M-Text-19M-LAION400M.pt")
    parser.add_argument("--out", default=DEFAULT_OUT)
    args = parser.parse_args()

    import torch

    sys.path.insert(0, HERE)
    import tinyclip

    model, _ = tinyclip.load(args.tinyclip, args.checkpoint)

    class ImageEncoder(torch.nn.Module):
        def __init__(self, clip):
            super().__init__()
            self.clip = clip

        def forward(self, image):
            return self.clip.encode_image(image)  # not normalized; the app normalizes

    encoder = ImageEncoder(model).eval()
    work = tempfile.mkdtemp(prefix="tinyclip_export_")
    try:
        onnx_path = os.path.join(work, "image_encoder.onnx")
        dummy = torch.from_numpy(test_inputs(1))
        torch.onnx.export(
            encoder, (dummy,), onnx_path, input_names=["image"], output_names=["embedding"],
            opset_version=17, dynamo=False,
        )
        print("ONNX:", onnx_path)

        saved_model = os.path.join(work, "saved_model")
        subprocess.run(
            [sys.executable, "-m", "onnx2tf", "-i", onnx_path, "-o", saved_model, "-osd", "-n"],
            check=True,
        )

        import tensorflow as tf

        converter = tf.lite.TFLiteConverter.from_saved_model(saved_model)
        converter.optimizations = [tf.lite.Optimize.DEFAULT]  # no dataset: dynamic-range int8 weights
        tflite = converter.convert()
        os.makedirs(os.path.dirname(args.out), exist_ok=True)
        with open(args.out, "wb") as f:
            f.write(tflite)
        print(f"TFLite: {args.out} ({len(tflite) / 1e6:.1f} MB)")

        # Same embeddings as PyTorch?
        inputs = test_inputs()
        with torch.no_grad():
            expected = encoder(torch.from_numpy(inputs)).numpy()
        interpreter = tf.lite.Interpreter(model_path=args.out)
        interpreter.allocate_tensors()
        inp = interpreter.get_input_details()[0]
        out = interpreter.get_output_details()[0]
        print("input", inp["shape"], inp["dtype"].__name__, "output", out["shape"])
        channels_last = list(inp["shape"]) == [1, 224, 224, 3]
        worst = 1.0
        for i in range(len(inputs)):
            x = inputs[i:i + 1]
            interpreter.set_tensor(inp["index"], np.transpose(x, (0, 2, 3, 1)) if channels_last else x)
            interpreter.invoke()
            got = interpreter.get_tensor(out["index"])[0]
            cos = float(np.dot(got, expected[i]) / (np.linalg.norm(got) * np.linalg.norm(expected[i])))
            worst = min(worst, cos)
        print(f"TFLite vs PyTorch cosine similarity, worst of {len(inputs)}: {worst:.4f}")
        if worst < 0.98:
            raise SystemExit("The quantized model drifted too far from PyTorch.")
    finally:
        shutil.rmtree(work, ignore_errors=True)


if __name__ == "__main__":
    main()
