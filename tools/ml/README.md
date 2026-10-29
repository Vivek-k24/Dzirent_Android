# Item recognition model (tools/ml)

Create listing recognizes the item in the cover photo **on the phone**: no network call,
no paid AI, no LLM. This folder makes the two files the app ships, and lets you check
the model on your own photos from your PC (no emulator needed).

| File in `android/app/src/main/assets/ml/` | Made by | What it is |
|---|---|---|
| `tinyclip_image.tflite` (19.6 MB) | `export_image_encoder.py` | TinyCLIP's image encoder only |
| `label_embeddings.json` (0.4 MB) | `make_text_embeddings.py` | one text embedding per label in `labels.py` |

The text encoder never ships: label texts are fixed, so their embeddings are computed here
once. The app turns a photo into an image embedding and picks the closest label.

## The model and its license

**TinyCLIP-ResNet-19M-Text-19M (LAION-400M)** from Microsoft,
[github.com/wkcn/TinyCLIP](https://github.com/wkcn/TinyCLIP). The license is **MIT** for the code and
the released weights, so commercial use is allowed. It is a distilled CLIP, about 19M parameters in
the image tower, with 56.4% zero-shot ImageNet top-1.

Why this one:

- **OpenAI CLIP ViT-B/32** is MIT too. But its image encoder is about 88M parameters, roughly
  88 MB even in int8, far over the 25 MB budget. OpenAI's model card also calls any deployed use
  "out of scope".
- **MobileCLIP-S0 is excluded.** Apple's weights are under the "Apple ML Research Model" terms,
  which are research-only and forbid commercial products.
- **Qualcomm AI Hub compiled files are not used** (proprietary license).
- **TinyCLIP-ViT-8M/16** is smaller (about 8 MB) but much weaker (41.1% ImageNet).
  **TinyCLIP-ViT-39M/16** is stronger (63.5%) but about 40 MB.

The weights were trained on LAION-400M, a web-scraped dataset. The license allows commercial
use; whether you're comfortable with the training data's provenance is your call. If not,
swap in the YFCC-15M-trained ViT-8M/16: change `ARCH`/`CHECKPOINT` in `tinyclip.py`, and
re-tune the thresholds.

## Setup (once)

```
py -3.12 -m venv .venv
.venv\Scripts\activate
pip install torch==2.15.0 torchvision==0.29.2 --index-url https://download.pytorch.org/whl/cpu
pip install -r requirements.txt
git clone https://github.com/wkcn/TinyCLIP.git  (the scripts used commit ad981bf)
```

Download the checkpoint (127 MB):
https://github.com/wkcn/TinyCLIP-model-zoo/releases/download/checkpoints/TinyCLIP-ResNet-19M-Text-19M-LAION400M.pt

## Check the model on your photos

```
python check_model.py photo1.jpg photo2.jpg photo3.jpg
```

For each photo the script prints the top 3 labels and what the app would show: a suggestion, a
policy warning, or nothing. With several photos it also prints how similar each one is to the
first, which is the app's "looks like a different item" check. It needs only `numpy`, `pillow`
and `ai-edge-litert`, not TinyCLIP.

## Remake the files

```
python make_text_embeddings.py --tinyclip TinyCLIP --checkpoint TinyCLIP-ResNet-19M-Text-19M-LAION400M.pt
python export_image_encoder.py --tinyclip TinyCLIP --checkpoint TinyCLIP-ResNet-19M-Text-19M-LAION400M.pt --images C:\some\clothing\photos
```

- **Labels.** `labels.py` lists them in the same order as `ItemLabels.kt`. To add one, add it in
  both places with its tier and category. The unit test `ModelAssetsTest` fails if they differ.
- **Export.** PyTorch goes to ONNX, then onnx2tf makes a float TFLite file. ai-edge-quantizer then
  stores the weights as int8. The stem and two shortcut convolutions stay float32 or float16:
  int8 on those layers alone drifts the embeddings noticeably. The script fails if the result
  drifts from PyTorch: it needs a cosine similarity of at least 0.96 on synthetic images and on
  your `--images`.
- **Recalibrating.** The thresholds (suggestion and policy confidence, same-item similarity) are
  in `PhotoThresholds.kt` (owner to tune), copied in `labels.py` for `check_model.py`.

## How the shipped model did on 40 sample photos

These are the website's demo catalog photos (Pexels), which were used only on this PC and not
shipped. Most labels came out right: saree 87%, lehenga 63%, tuxedo 88%, jumpsuit 96%,
cocktail dress 83%, gown 50-87%.

Weak spots, which are worth checking on your own photos:

- Kurta sets read as lehenga.
- Dresses worn by models score some "lingerie": up to 39%, below the 60% policy cutoff.
- Plain trousers on a model read as a suit or tuxedo.

Different items scored a median similarity of 0.40, and 99% were below 0.74. The same photo
mirrored, cropped and darkened scored at least 0.82. The same-item cutoff is 0.65.
