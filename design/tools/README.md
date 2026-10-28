# Logo tools

Regenerate the logo assets from `flower_params.json` (the flower, fitted to
`../logo-source.png`) and `wordmark.py` (DZIRENT with the custom N).

```sh
python -m venv .venv && .venv/Scripts/python -m pip install pillow numpy skia-python
cd design/tools
../../.venv/Scripts/python build_assets.py flower_params.json ../.. <scratch dir>
../../.venv/Scripts/python previews.py flower_params.json ../.. <scratch dir>
```

`build_assets.py` writes `res/drawable/logo_mark.xml`, `logo_wordmark.xml`,
`ic_launcher_foreground.xml`, `ic_launcher_monochrome.xml`, the SVGs in `design/` and
`design/play-store-icon-512.png`. `previews.py` writes the PNGs in `design/previews/`.
