"""Turn fitted parameters into clean vector outlines in a size x size box centred in it:
the hub ring, then for each of the six arms the spoke-and-petal stroke and (if fitted) the
thin outer arc. Each stroke outline is a smooth closed Catmull-Rom spline (cubic Beziers)
through points on both edges of the variable-width centerline."""
import numpy as np

N_ARMS = 6


def f(v):
    s = ("%.2f" % v).rstrip("0").rstrip(".")
    return "0" if s in ("-0", "") else s


def smoothstep(a, b, t):
    t = min(max(t, 0.0), 1.0)
    return a + (b - a) * (t * t * (3 - 2 * t))


def main_centerline(p, n=60):
    R1, Rm, Re, phm, phe = p["R1"], p["Rm"], p["Re"], p["phm"], p["phe"]
    w0, w1, w2, w3, bend = p["w0"], p["w1"], p["w2"], p["w3"], p["bend"]
    r_start = (p["hub"] + p["hole"]) / 2  # starts inside the hub ring
    pts = []
    for t in np.linspace(0, 1, 14):
        pts.append((r_start + (R1 - r_start) * t, bend * t * t, smoothstep(w0, w1, t)))
    for t in np.linspace(0, 1, n)[1:]:
        ph = bend + (phe - bend) * t
        if ph <= phm:
            r = R1 + (Rm - R1) * np.sin((ph - bend) / max(phm - bend, 1e-3) * np.pi / 2)
        else:
            r = Rm + (Re - Rm) * (1 - np.cos((ph - phm) / max(phe - phm, 1e-3) * np.pi / 2))
        w = smoothstep(w1, w2, t * 2) if t < 0.5 else smoothstep(w2, w3, (t - 0.5) * 2)
        pts.append((r, ph, w))
    return pts


def outer_centerline(p, n=40):
    if "outer_s0" not in p:
        return None
    s0, r0, rp, php, phe, re = (p["outer_" + k] for k in ["s0", "r0", "rp", "php", "phe", "re"])
    wa, wb, wc = p["outer_wa"], p["outer_wb"], p["outer_wc"]
    pts = []
    for t in np.linspace(0, 1, n):
        ph = s0 + (phe - s0) * t
        if ph <= php:
            r = r0 + (rp - r0) * np.sin((ph - s0) / max(php - s0, 1e-3) * np.pi / 2)
        else:
            r = rp + (re - rp) * (1 - np.cos((ph - php) / max(phe - php, 1e-3) * np.pi / 2))
        w = smoothstep(wa, wb, t * 2) if t < 0.5 else smoothstep(wb, wc, (t - 0.5) * 2)
        pts.append((r, ph, w))
    return pts


def catmull_rom(P):
    n = len(P)
    out = "M%s,%s" % (f(P[0][0]), f(P[0][1]))
    for i in range(n):
        p0, p1, p2, p3 = P[(i - 1) % n], P[i], P[(i + 1) % n], P[(i + 2) % n]
        c1 = (p1[0] + (p2[0] - p0[0]) / 6, p1[1] + (p2[1] - p0[1]) / 6)
        c2 = (p2[0] - (p3[0] - p1[0]) / 6, p2[1] - (p3[1] - p1[1]) / 6)
        out += "C%s,%s %s,%s %s,%s" % (f(c1[0]), f(c1[1]), f(c2[0]), f(c2[1]), f(p2[0]), f(p2[1]))
    return out + "Z"


def stroke_outline(cl, k, th, scale, cx, cy, round_start, keep=4):
    xy = []
    for r, ph, w in cl:
        a = th + k * 2 * np.pi / N_ARMS + ph
        xy.append((cx + r * scale * np.cos(a), cy + r * scale * np.sin(a), w * scale))
    xy = np.array(xy)
    left, right = [], []
    for i in range(len(xy)):
        x, y, w = xy[i]
        j0, j1 = max(i - 1, 0), min(i + 1, len(xy) - 1)
        dx, dy = xy[j1][0] - xy[j0][0], xy[j1][1] - xy[j0][1]
        L = np.hypot(dx, dy) or 1
        nx, ny = -dy / L, dx / L
        left.append((x + nx * w / 2, y + ny * w / 2))
        right.append((x - nx * w / 2, y - ny * w / 2))

    def cap(i, j):
        x, y, w = xy[i]
        base = np.arctan2(xy[i][1] - xy[j][1], xy[i][0] - xy[j][0])
        return [(x + w / 2 * np.cos(base + a), y + w / 2 * np.sin(base + a))
                for a in np.linspace(np.pi / 2, -np.pi / 2, 5)[1:-1]]

    sel = lambda pts: pts[::keep] + ([pts[-1]] if (len(pts) - 1) % keep else [])
    pts = sel(left) + cap(len(xy) - 1, len(xy) - 2) + sel(right)[::-1]
    if round_start:
        pts += cap(0, 1)
    return catmull_rom(pts)


def hub_path(p, scale, cx, cy):
    """A disk with a round hole: two circles drawn in opposite directions (nonZero)."""
    R, r = p["hub"] * scale, p["hole"] * scale
    outer = "M%s,%sA%s,%s 0 1 1 %s,%sA%s,%s 0 1 1 %s,%sZ" % (f(cx - R), f(cy), f(R), f(R), f(cx + R), f(cy), f(R), f(R), f(cx - R), f(cy))
    inner = "M%s,%sA%s,%s 0 1 0 %s,%sA%s,%s 0 1 0 %s,%sZ" % (f(cx - r), f(cy), f(r), f(r), f(cx + r), f(cy), f(r), f(r), f(cx - r), f(cy))
    return outer + inner


def extent(p):
    lines = [main_centerline(p)] + ([outer_centerline(p)] if outer_centerline(p) else [])
    return max(r + w / 2 for cl in lines for r, _, w in cl)


def flower_paths(p, size=100.0):
    """Paths for the flower filling a size x size box."""
    scale = (size / 2) / extent(p)
    c = size / 2
    paths = [hub_path(p, scale, c, c)]
    outer = outer_centerline(p)
    for k in range(N_ARMS):
        paths.append(stroke_outline(main_centerline(p), k, p["th"], scale, c, c, round_start=False))
        if outer:
            paths.append(stroke_outline(outer, k, p["th"], scale, c, c, round_start=True))
    return paths
