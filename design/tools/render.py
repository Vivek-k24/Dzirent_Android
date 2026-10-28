"""Render an SVG file to PNG with skia: render.py in.svg out.png width height."""
import sys
import skia

svg, out, w, h = sys.argv[1], sys.argv[2], int(sys.argv[3]), int(sys.argv[4])
stream = skia.Stream.MakeFromFile(svg)
dom = skia.SVGDOM.MakeFromStream(stream)
dom.setContainerSize(skia.Size(w, h))
surface = skia.Surface(w, h)
canvas = surface.getCanvas()
canvas.clear(skia.ColorTRANSPARENT)
dom.render(canvas)
surface.makeImageSnapshot().save(out, skia.kPNG)
print(out)
