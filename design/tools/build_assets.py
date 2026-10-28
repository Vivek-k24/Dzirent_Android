"""Write the logo assets into the repo: build_assets.py <arm params json> <repo root> <scratch>."""
import json, os, subprocess, sys
import flower, wordmark

params = json.load(open(sys.argv[1]))
ROOT = sys.argv[2]
SCRATCH = sys.argv[3]
RES = os.path.join(ROOT, "android", "app", "src", "main", "res")
DESIGN = os.path.join(ROOT, "design")
MAROON = "#A80038"
INK = "#111111"


def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(text)


def vector(width_dp, height_dp, vw, vh, paths, color, comment, group=None):
    body = "".join('\n%s<path\n%s    android:fillColor="%s"\n%s    android:pathData="%s" />' % (
        "        " if group else "    ", "        " if group else "    ", color, "        " if group else "    ", d) for d in paths)
    if group:
        body = '\n    <group %s>%s\n    </group>' % (group, body)
    return ('<?xml version="1.0" encoding="utf-8"?>\n<!-- %s -->\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="%sdp"\n    android:height="%sdp"\n'
            '    android:viewportWidth="%s"\n    android:viewportHeight="%s">%s\n</vector>\n'
            % (comment, width_dp, height_dp, wordmark.f(vw), wordmark.f(vh), body))


def svg(vw, vh, layers, bg=None):
    parts = ['<rect width="%s" height="%s" fill="%s"/>' % (wordmark.f(vw), wordmark.f(vh), bg)] if bg else []
    for color, paths, transform in layers:
        g = "".join('<path d="%s"/>' % d for d in paths)
        parts.append('<g fill="%s"%s>%s</g>' % (color, ' transform="%s"' % transform if transform else "", g))
    return ('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 %s %s" width="%s" height="%s">%s</svg>\n'
            % (wordmark.f(vw), wordmark.f(vh), wordmark.f(vw), wordmark.f(vh), "".join(parts)))


mark = flower.flower_paths(params, 100)
word = [wordmark.wordmark_path()]
WW = wordmark.WIDTH

# --- App drawables (tinted at runtime, so the fill is a neutral black) ---
write(os.path.join(RES, "drawable", "logo_mark.xml"),
      vector(24, 24, 100, 100, mark, "#FF000000",
             "Dzirent flower mark. Drawn in black and tinted in code (maroon, or lighter in dark mode)."))
write(os.path.join(RES, "drawable", "logo_wordmark.xml"),
      vector("%.1f" % (16 * WW / 100), 16, WW, 100, word, "#FF000000",
             "DZIRENT wordmark with the custom N. Drawn in black and tinted in code (ink or white)."))

# --- Launcher icon: 108dp layers; the flower sits inside the 66dp safe circle ---
icon_mark = flower.flower_paths(params, 60)  # 60 x 60 box
group = 'android:translateX="24" android:translateY="24"'
write(os.path.join(RES, "drawable", "ic_launcher_foreground.xml"),
      vector(108, 108, 108, 108, icon_mark, "#FF" + MAROON[1:], "Launcher icon foreground: the flower mark.", group))
write(os.path.join(RES, "drawable", "ic_launcher_monochrome.xml"),
      vector(108, 108, 108, 108, icon_mark, "#FFFFFFFF", "Themed (monochrome) launcher icon: the system tints it.", group))

# --- design/ sources ---
write(os.path.join(DESIGN, "logo-mark.svg"), svg(100, 100, [(MAROON, mark, None)]))
write(os.path.join(DESIGN, "logo-wordmark.svg"), svg(WW, 100, [(INK, word, None)]))
write(os.path.join(DESIGN, "logo-wordmark-dark.svg"), svg(WW, 100, [("#FFFFFF", word, None)]))
# Full logo: mark above the wordmark, centred.
full_w = WW
full_h = 100 + 30 + 100
mx = (full_w - 100) / 2
write(os.path.join(DESIGN, "logo-full.svg"),
      svg(full_w, full_h, [(MAROON, mark, "translate(%s,0)" % wordmark.f(mx)), (INK, word, "translate(0,130)")]))
# Play Store icon: 512 x 512, white, flower ~66% wide.
play = svg(512, 512, [(MAROON, flower.flower_paths(params, 340), "translate(86,86)")], bg="#FFFFFF")
play_svg = os.path.join(SCRATCH, "play-store-icon-512.svg")
write(play_svg, play)
subprocess.check_call([sys.executable, "-I", os.path.join(os.path.dirname(__file__), "render.py"),
                       play_svg, os.path.join(DESIGN, "play-store-icon-512.png"), "512", "512"])
print("assets written")
