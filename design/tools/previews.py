"""PNG previews of the top bar (light, dark) and the launcher icon: previews.py params repo scratch."""
import json, os, subprocess, sys
import flower, wordmark

p = json.load(open(sys.argv[1]))
OUT = os.path.join(sys.argv[2], "design", "previews")
SCRATCH = sys.argv[3]
os.makedirs(OUT, exist_ok=True)
render = os.path.join(os.path.dirname(os.path.abspath(__file__)), "render.py")
mark = flower.flower_paths(p, 100)
word = wordmark.wordmark_path()
WW = wordmark.WIDTH
DP = 3  # 3 px per dp (xxhdpi-ish)


def png(name, svg_text, w, h):
    path = os.path.join(SCRATCH, name + ".svg")
    open(path, "w", encoding="utf-8").write(svg_text)
    subprocess.check_call([sys.executable, "-I", render, path, os.path.join(OUT, name + ".png"), str(w), str(h)],
                          stdout=subprocess.DEVNULL)


def g(color, paths, transform):
    return '<g fill="%s" transform="%s">%s</g>' % (color, transform, "".join('<path d="%s"/>' % d for d in paths))


def top_bar(name, bg, mark_color, ink, avatar_bg, avatar_ink):
    # 360dp x 56dp bar, as in HomeTopBar: avatar left, logo centred (22dp mark, 6dp gap,
    # 13dp-high wordmark), location right.
    W, H = 360, 56
    word_h = 13.0
    word_w = word_h * WW / 100
    total = 22 + 6 + word_w
    x0 = (W - total) / 2
    parts = ['<rect width="%d" height="%d" fill="%s"/>' % (W, H, bg),
             '<circle cx="32" cy="28" r="16" fill="%s"/>' % avatar_bg,
             '<text x="32" y="33" font-family="sans-serif" font-size="14" font-weight="bold" fill="%s" text-anchor="middle">T</text>' % avatar_ink,
             g(mark_color, mark, "translate(%.2f,%.2f) scale(0.22)" % (x0, (H - 22) / 2)),
             g(ink, [word], "translate(%.2f,%.2f) scale(%.4f)" % (x0 + 28, (H - word_h) / 2, word_h / 100)),
             '<text x="348" y="32" font-family="sans-serif" font-size="11" fill="%s" text-anchor="end">Austin, TX</text>' % ink]
    svg = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 %d %d" width="%d" height="%d">%s</svg>' % (W, H, W * DP, H * DP, "".join(parts))
    png(name, svg, W * DP, H * DP)


top_bar("topbar-light", "#FFFFFF", "#A80038", "#111111", "#F2F2F2", "#111111")
top_bar("topbar-dark", "#0E0E0E", "#E8638A", "#FFFFFF", "#2A2A2A", "#FFFFFF")

# Launcher icon: the 108dp adaptive layers masked to a circle (what most launchers show),
# a rounded square, and the themed (monochrome) icon as Android 13+ tints it.
icon_mark = flower.flower_paths(p, 60)
fg = lambda color: g(color, icon_mark, "translate(24,24)")


def icon(name, mask, bg, color):
    defs = '<defs><clipPath id="m">%s</clipPath></defs>' % mask
    body = '<g clip-path="url(#m)"><rect width="108" height="108" fill="%s"/>%s</g>' % (bg, fg(color))
    svg = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="432" height="432">%s%s</svg>' % (defs, body)
    png(name, svg, 432, 432)


# The visible part of an adaptive icon is the inner 72dp (18dp cut from each side).
circle = '<circle cx="54" cy="54" r="36"/>'
squircle = '<rect x="18" y="18" width="72" height="72" rx="18" ry="18"/>'
icon("launcher-icon-circle", circle, "#FFFFFF", "#A80038")
icon("launcher-icon-rounded-square", squircle, "#FFFFFF", "#A80038")
icon("launcher-icon-themed", circle, "#D7E3F4", "#1C3A5E")
print("previews written to", OUT)
