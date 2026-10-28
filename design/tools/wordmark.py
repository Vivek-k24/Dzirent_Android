"""DZIRENT wordmark as path data, cap height 100. Letters are built from straight strokes and
arcs (stroke 20) at the positions and widths measured from the source; N is the custom arch
with separate feet."""

S = 20.0  # stroke


def f(v):
    s = ("%.2f" % v).rstrip("0").rstrip(".")
    return "0" if s in ("-0", "") else s


def rect(x0, y0, x1, y1):
    return "M%s,%sH%sV%sH%sZ" % (f(x0), f(y0), f(x1), f(y1), f(x0))


def poly(*pts):
    return "M" + "L".join("%s,%s" % (f(x), f(y)) for x, y in pts) + "Z"


def rounded_right(x0, y0, x1, y1, r):
    """Rectangle with its two right corners rounded (radius r), clockwise."""
    return ("M%s,%sH%sA%s,%s 0 0 1 %s,%sV%sA%s,%s 0 0 1 %s,%sH%sZ" %
            (f(x0), f(y0), f(x1 - r), f(r), f(r), f(x1), f(y0 + r), f(y1 - r), f(r), f(r), f(x1 - r), f(y1), f(x0)))


def rounded_right_ccw(x0, y0, x1, y1, r):
    """Same shape, counter-clockwise: a hole when combined with a clockwise outline (nonZero)."""
    return ("M%s,%sV%sH%sA%s,%s 0 0 0 %s,%sV%sA%s,%s 0 0 0 %s,%sZ" %
            (f(x0), f(y0), f(y1), f(x1 - r), f(r), f(r), f(x1), f(y1 - r), f(y0 + r), f(r), f(r), f(x1 - r), f(y0)))


def letter_D(x, w=68.3):
    R = 30.0
    return rounded_right(x, 0, x + w, 100, R) + rounded_right_ccw(x + S, S, x + w - S, 100 - S, R - S)


def letter_Z(x, w=61.0):
    return poly((x, 0), (x + w, 0), (x + w, S), (x + 24, 100 - S), (x + w, 100 - S), (x + w, 100),
                (x, 100), (x, 100 - S), (x + w - 24, S), (x, S))


def letter_I(x):
    return rect(x, 0, x + S, 100)


def letter_R(x, w=61.0):
    bowl_w, bowl_h, R = w - 3, 58.0, 24.0
    outline = rounded_right(x, 0, x + bowl_w, bowl_h, R)
    counter = rounded_right_ccw(x + S, S, x + bowl_w - S, bowl_h - S, R - S)
    stem = rect(x, bowl_h - 1, x + S, 100)
    leg = poly((x + 24, bowl_h - 2), (x + 46, bowl_h - 2), (x + w, 100), (x + w - 22, 100))
    return outline + counter + stem + leg


def letter_E(x, w=53.7):
    return (rect(x, 0, x + S, 100) + rect(x, 0, x + w, S) + rect(x, 40, x + w - 4, 40 + S) +
            rect(x, 100 - S, x + w, 100))


def letter_N(x, w=87.8):
    """Custom N: a solid arch whose legs stop above separate feet (the thin cut)."""
    R = w / 2
    leg = 23.0
    r = R - leg
    cut_top, cut_bottom = 78.0, 82.0
    arch = ("M%s,%sV%sA%s,%s 0 0 1 %s,%sV%sH%sV%sA%s,%s 0 0 0 %s,%sV%sZ" %
            (f(x), f(cut_top), f(R), f(R), f(R), f(x + w), f(R), f(cut_top), f(x + w - leg), f(R),
             f(r), f(r), f(x + leg), f(R), f(cut_top)))
    return arch + rect(x, cut_bottom, x + leg, 100) + rect(x + w - leg, cut_bottom, x + w, 100)


def letter_T(x, w=63.4):
    stem_x = x + (w - S) / 2
    return rect(x, 0, x + w, S) + rect(stem_x, S - 1, stem_x + S, 100)


# Left edges measured from the source (px - 120) * 100 / 41.
POS = dict(D=0.0, Z=95.1, I=187.8, R=236.6, E=324.4, N=395.1, T=500.0)
WIDTH = 563.4


def wordmark_path():
    return (letter_D(POS["D"]) + letter_Z(POS["Z"]) + letter_I(POS["I"]) + letter_R(POS["R"]) +
            letter_E(POS["E"]) + letter_N(POS["N"]) + letter_T(POS["T"]))
