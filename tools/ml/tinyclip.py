"""Loads TinyCLIP (github.com/wkcn/TinyCLIP, MIT) from a local clone and checkpoint.

TinyCLIP ships its own copy of open_clip (with its model configs), so the clone's src/ folder
goes on the import path. Nothing is downloaded here: pass the paths of files you fetched.
"""

import os
import sys

ARCH = "TinyCLIP-ResNet-19M-Text-19M"
CHECKPOINT = "TinyCLIP-ResNet-19M-Text-19M-LAION400M.pt"
CHECKPOINT_URL = (
    "https://github.com/wkcn/TinyCLIP-model-zoo/releases/download/checkpoints/" + CHECKPOINT
)
# The TinyCLIP commit the scripts were run against.
TINYCLIP_COMMIT = "ad981bf308f007c4a47c1b34786cc90bbfd2c43c"


def load(tinyclip_dir: str, checkpoint: str):
    """Returns (model, open_clip module), model in eval mode on the CPU."""
    src = os.path.join(os.path.abspath(tinyclip_dir), "src")
    if not os.path.isdir(os.path.join(src, "open_clip")):
        raise SystemExit(f"{tinyclip_dir} doesn't look like a TinyCLIP clone (no src/open_clip)")
    if not os.path.isfile(checkpoint):
        raise SystemExit(f"Checkpoint not found: {checkpoint}\nDownload it from {CHECKPOINT_URL}")
    sys.path.insert(0, src)
    import open_clip  # noqa: E402  (TinyCLIP's copy)
    import torch

    # The checkpoint is a plain state dict; weights_only keeps torch.load from running pickled code.
    original_load = torch.load

    def safe_load(*args, **kwargs):
        kwargs.setdefault("weights_only", True)
        return original_load(*args, **kwargs)

    torch.load = safe_load
    try:
        model = open_clip.create_model(ARCH, pretrained=checkpoint)
    finally:
        torch.load = original_load
    model.eval()
    return model, open_clip
