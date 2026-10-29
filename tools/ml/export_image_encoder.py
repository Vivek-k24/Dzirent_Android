"""Exports TinyCLIP's image encoder to a quantized TFLite (LiteRT) model for the app.

    python export_image_encoder.py --tinyclip PATH/TO/TinyCLIP --checkpoint PATH/TO/ckpt.pt
                                   [--images FOLDER_OF_PHOTOS]

Steps:
1. PyTorch -> ONNX (opset 17), with the attention pooling rewritten in plain matrix products
   (same math; see replace_attention_pool).
2. ONNX -> float32 TFLite with onnx2tf (NHWC input, 1 x 224 x 224 x 3).
3. Weight-only quantization with ai-edge-quantizer: weights are stored as int8 (per channel)
   and turned back into floats when the model loads, so the math runs in float32. Plain
   dynamic-range quantization drifted too far on this model (cosine ~0.84 against float).
   The stem and the shortcut ("downsample") convolutions are the layers that drift when
   quantized, so the small ones stay float32 and two larger ones float16 (KEEP_FLOAT32 / KEEP_FLOAT16).
4. Checks the result against PyTorch (cosine similarity of the embeddings) on synthetic test
   images, plus your photos if you pass --images.

Only the image encoder ships; the text encoder stays here (make_text_embeddings.py).
Output: android/app/src/main/assets/ml/tinyclip_image.tflite
"""

import argparse
import glob
import os
import re
import shutil
import sys
import tempfile

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(os.path.dirname(HERE))
DEFAULT_OUT = os.path.join(REPO, "android", "app", "src", "main", "assets", "ml", "tinyclip_image.tflite")
MEAN = np.array([0.48145466, 0.4578275, 0.40821073], np.float32)
STD = np.array([0.26862954, 0.26130258, 0.27577711], np.float32)

# Convolutions kept out of int8 (found by quantizing one layer at a time and measuring the
# drift on 40 clothing photos). Names are PyTorch module paths inside model.visual.
# layer4.0.downsample.0 (1M weights) also drifts a little in int8; keeping it float16 too
# would add 1 MB (cosine min 0.972 instead of 0.965) and push the app over its size budget.
KEEP_FLOAT32 = ["conv1", "conv2", "conv3", "layer1.0.downsample.0", "layer2.0.conv1"]  # all small
KEEP_FLOAT16 = ["layer2.0.downsample.0", "layer3.0.downsample.0"]
MIN_SIMILARITY = 0.96


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


def test_images_nhwc(n: int = 4) -> np.ndarray:
    """test_inputs() back in 0-1 pixel values, channels last, the form onnx2tf samples with."""
    x = test_inputs(n) * STD[None, :, None, None] + MEAN[None, :, None, None]
    return np.transpose(x, (0, 2, 3, 1)).astype(np.float32)


def photo_inputs(folder: str) -> np.ndarray:
    """Your photos, preprocessed exactly as check_model.py (and the app) do, NCHW."""
    import check_model

    paths = sorted(p for ext in ("jpg", "jpeg", "png", "webp") for p in glob.glob(os.path.join(folder, f"*.{ext}")))
    if not paths:
        raise SystemExit(f"No photos in {folder}")
    return np.stack([np.transpose(check_model.preprocess(p), (2, 0, 1)) for p in paths]).astype(np.float32)


def replace_attention_pool(pool) -> None:
    """Swaps ModifiedResNet's attention pooling for the same math in 2D/3D tensors.

    The original runs F.multi_head_attention_forward on (tokens, batch, channels) tensors, which
    onnx2tf mistakes for an image layout. Only the first token (the mean of the feature map)
    is ever used as the output, so this computes attention for that one query, per head with
    batched matrix products. Batch size 1, as the app runs it.
    """
    import torch

    def forward(x):
        _, channels, height, width = x.shape
        heads = pool.num_heads
        dim = channels // heads
        tokens = x.reshape(channels, height * width).transpose(0, 1)  # (HW, C)
        tokens = torch.cat([tokens.mean(dim=0, keepdim=True), tokens], dim=0)  # (HW+1, C)
        tokens = tokens + pool.positional_embedding.to(tokens.dtype)
        length = tokens.shape[0]
        q = pool.q_proj(tokens[:1]).reshape(heads, 1, dim) * dim ** -0.5  # (h, 1, d)
        k = pool.k_proj(tokens).reshape(length, heads, dim).permute(1, 2, 0)  # (h, d, L)
        v = pool.v_proj(tokens).reshape(length, heads, dim).permute(1, 0, 2)  # (h, L, d)
        attention = torch.softmax(torch.bmm(q, k), dim=-1)  # (h, 1, L)
        out = torch.bmm(attention, v).reshape(1, channels)  # heads back side by side
        return pool.c_proj(out)

    pool.forward = forward


def tflite_conv_scopes(onnx_path: str, modules: list[str]) -> dict[str, str]:
    """Regexes for the TFLite CONV_2D ops of the given PyTorch modules.

    onnx2tf names the N-th ONNX Conv "tf.nn.convolution_N" (the first one has no suffix), and the
    legacy ONNX exporter names each Conv after its module ("/.../layer1/layer1.0/downsample/
    downsample.0/Conv"). ai-edge-quantizer matches a regex against the op's output tensor
    names joined with ";" plus a trailing ";".
    """
    import onnx

    convs = [n for n in onnx.load(onnx_path).graph.node if n.op_type == "Conv"]
    scopes = {}
    for module in modules:
        # "layer1.0.downsample.0" -> ".../visual/layer1/layer1.0/downsample/downsample.0/Conv"
        wanted = "/visual/" + "/".join(_expand(module.split("."))) + "/Conv"
        index = next((i for i, n in enumerate(convs) if n.name.endswith(wanted)), None)
        if index is None:
            raise SystemExit(f"No ONNX Conv for {module} ({wanted})")
        name = "tf.nn.convolution" + ("" if index == 0 else f"_{index}")
        scopes[module] = re.escape(name + "/convolution") + ";$"
    return scopes


def _expand(parts: list[str]) -> list[str]:
    """['layer1', '0', 'downsample', '0'] -> ['layer1', 'layer1.0', 'downsample', 'downsample.0']."""
    out: list[str] = []
    for part in parts:
        if part.isdigit():
            out.append(out[-1] + "." + part)
        else:
            out.append(part)
    return out


def quantize(float_model: str, out_path: str, onnx_path: str) -> None:
    from ai_edge_quantizer import qtyping, quantizer, recipe_manager
    from ai_edge_quantizer.algorithm_manager import AlgorithmName

    op = qtyping.TFLOperationName
    rm = recipe_manager.RecipeManager()
    for kind in (op.CONV_2D, op.FULLY_CONNECTED):
        rm.add_weight_only_config(regex=".*", operation_name=kind, num_bits=8,
                                  algorithm_key=AlgorithmName.MIN_MAX_UNIFORM_QUANT)
    scopes = tflite_conv_scopes(onnx_path, KEEP_FLOAT32 + KEEP_FLOAT16)
    for module in KEEP_FLOAT16:
        rm.add_weight_only_config(regex=scopes[module], operation_name=op.CONV_2D, num_bits=16,
                                  algorithm_key=AlgorithmName.FLOAT_CASTING)
    for module in KEEP_FLOAT32:
        rm.add_quantization_config(regex=scopes[module], operation_name=op.CONV_2D,
                                   algorithm_key=AlgorithmName.NO_QUANTIZE)
    q = quantizer.Quantizer(float_model)
    q.load_quantization_recipe(rm.get_quantization_recipe())
    q.quantize().export_model(out_path, overwrite=True)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--tinyclip", required=True, help="local clone of github.com/wkcn/TinyCLIP")
    parser.add_argument("--checkpoint", required=True, help="TinyCLIP-ResNet-19M-Text-19M-LAION400M.pt")
    parser.add_argument("--images", help="optional folder of clothing photos to check the result on")
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

    def embed_each(inputs: np.ndarray) -> np.ndarray:
        with torch.no_grad():
            return np.concatenate([encoder(torch.from_numpy(inputs[i:i + 1])).numpy() for i in range(len(inputs))])

    before = embed_each(test_inputs())
    replace_attention_pool(model.visual.attnpool)
    after = embed_each(test_inputs())
    print(f"Attention pool rewrite, largest difference: {float(np.abs(before - after).max()):.2e}")

    work = tempfile.mkdtemp(prefix="tinyclip_export_")
    try:
        onnx_path = os.path.join(work, "image_encoder.onnx")
        torch.onnx.export(
            encoder, (torch.from_numpy(test_inputs(1)),), onnx_path, input_names=["image"],
            output_names=["embedding"], opset_version=17, dynamo=False,
        )

        import onnx2tf
        import onnx2tf.onnx2tf as onnx2tf_main

        # onnx2tf would download (and unpickle) sample images to test the conversion; give it
        # our own synthetic ones instead, so nothing is fetched or unpickled.
        onnx2tf_main.download_test_image_data = lambda: test_images_nhwc()
        saved_model = os.path.join(work, "saved_model")
        onnx2tf.convert(
            input_onnx_file_path=onnx_path, output_folder_path=saved_model,
            output_signaturedefs=True, not_use_onnxsim=False, non_verbose=True,
        )
        float_model = os.path.join(saved_model, "image_encoder_float32.tflite")

        os.makedirs(os.path.dirname(args.out), exist_ok=True)
        quantize(float_model, args.out, onnx_path)
        print(f"TFLite: {args.out} ({os.path.getsize(args.out) / 1e6:.1f} MB)")

        # Same embeddings as PyTorch? Checked with TensorFlow's interpreter, which is older than
        # the app's LiteRT, so it also shows the app's runtime can load these ops.
        import tensorflow as tf

        interpreter = tf.lite.Interpreter(model_path=args.out)
        interpreter.allocate_tensors()
        inp = interpreter.get_input_details()[0]
        out = interpreter.get_output_details()[0]
        print("input", list(inp["shape"]), inp["dtype"].__name__, "output", list(out["shape"]))
        sets = {"synthetic": test_inputs()}
        if args.images:
            sets["your photos"] = photo_inputs(args.images)
        for name, inputs in sets.items():
            expected = embed_each(inputs)
            similarities = []
            for i in range(len(inputs)):
                interpreter.set_tensor(inp["index"], np.transpose(inputs[i:i + 1], (0, 2, 3, 1)))
                interpreter.invoke()
                got = interpreter.get_tensor(out["index"])[0]
                similarities.append(float(got @ expected[i] / (np.linalg.norm(got) * np.linalg.norm(expected[i]))))
            print(f"TFLite vs PyTorch on {len(inputs)} {name}: cosine min {min(similarities):.4f}, "
                  f"mean {np.mean(similarities):.4f}")
            if min(similarities) < MIN_SIMILARITY:
                raise SystemExit("The quantized model drifted too far from PyTorch.")
    finally:
        shutil.rmtree(work, ignore_errors=True)


if __name__ == "__main__":
    main()
