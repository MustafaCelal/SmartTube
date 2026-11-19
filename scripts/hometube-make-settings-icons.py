# HomeTube: thin outline settings icons (white on transparent), drawn at 8x and downsampled
import math, sys
from PIL import Image, ImageDraw, ImageFilter, ImageChops
OUT = sys.argv[1]
U = 8            # supersampling
N = 96           # design grid
S = N * U
SW = 6           # stroke width in grid units
W = (255, 255, 255, 255)

def P(v): return int(round(v * U))
def new(): return Image.new('L', (S, S), 0)
def outline(mask, w=SW):
    k = P(w) * 2 + 1
    k = k if k % 2 else k + 1
    # erode in steps (MinFilter size limit)
    eroded = mask
    steps = max(1, P(w) // 8)
    size = (P(w) * 2) // steps + 1
    if size % 2 == 0: size += 1
    for _ in range(steps): eroded = eroded.filter(ImageFilter.MinFilter(size))
    return ImageChops.subtract(mask, eroded)
def line(img, pts, w=SW):
    d = ImageDraw.Draw(img)
    pts = [(P(x), P(y)) for x, y in pts]
    d.line(pts, fill=255, width=P(w), joint='curve')
    r = P(w) / 2
    for x, y in (pts[0], pts[-1]): d.ellipse((x - r, y - r, x + r, y + r), fill=255)
def ring(img, cx, cy, r, w=SW):
    d = ImageDraw.Draw(img); d.ellipse((P(cx-r), P(cy-r), P(cx+r), P(cy+r)), outline=255, width=P(w))
def rrect(img, x0, y0, x1, y1, rad, w=SW):
    d = ImageDraw.Draw(img); d.rounded_rectangle((P(x0), P(y0), P(x1), P(y1)), radius=P(rad), outline=255, width=P(w))
def dot(img, cx, cy, r):
    d = ImageDraw.Draw(img); d.ellipse((P(cx-r), P(cy-r), P(cx+r), P(cy+r)), fill=255)
def arc(img, box, a0, a1, w=SW):
    d = ImageDraw.Draw(img); d.arc(tuple(P(v) for v in box), a0, a1, fill=255, width=P(w))

def save(name, img):
    rgba = Image.new('RGBA', (S, S), (255, 255, 255, 0))
    rgba.putalpha(img)
    rgba = rgba.resize((144, 144), Image.LANCZOS)
    rgba.save(f'{OUT}/{name}.png')

# account
m = new(); ring(m, 48, 48, 38); ring(m, 48, 38, 12)
arc(m, (22, 58, 74, 104), 200, 340); save('hts_account', m)
# cast
m = new(); line(m, [(14, 34), (14, 24), (82, 24), (82, 72), (52, 72)])
arc(m, (-14, 44, 46, 104), 270, 360); arc(m, (0, 58, 32, 90), 270, 360); dot(m, 16, 74, 5); save('hts_cast', m)
# language (globe)
m = new(); ring(m, 48, 48, 38)
d = ImageDraw.Draw(m); d.ellipse((P(32), P(10), P(64), P(86)), outline=255, width=P(SW))
line(m, [(12, 48), (84, 48)]); line(m, [(18, 30), (78, 30)]); line(m, [(18, 66), (78, 66)]); save('hts_language', m)
# general (gear)
g = new(); d = ImageDraw.Draw(g)
pts = []
for k in range(8):
    c = k * 45
    for a, r in ((c - 22.5 + 2, 31), (c - 10, 31), (c - 7, 40), (c + 7, 40), (c + 10, 31), (c + 22.5 - 2, 31)):
        pts.append((P(48 + r * math.cos(math.radians(a))), P(48 + r * math.sin(math.radians(a)))))
d.polygon(pts, fill=255)
gear = outline(g); ring(gear, 48, 48, 11); save('hts_general', gear)
# main UI (dashboard 2x2)
m = new()
for x0, y0 in ((12, 12), (52, 12), (12, 52), (52, 52)): rrect(m, x0, y0, x0 + 32, y0 + 32, 7)
save('hts_ui', m)
# player
m = new(); rrect(m, 10, 18, 86, 78, 10)
t = new(); ImageDraw.Draw(t).polygon([(P(40), P(34)), (P(40), P(62)), (P(62), P(48))], fill=255); m = ImageChops.lighter(m, t); save('hts_player', m)
# AFR (refresh rate: screen with wave)
m = new(); rrect(m, 10, 18, 86, 78, 10)
wave = [(22 + i, 48 - 10 * math.sin((i / 52) * 2 * math.pi)) for i in range(0, 53, 2)]
line(m, wave); save('hts_afr', m)
# subtitles
m = new(); rrect(m, 10, 18, 86, 78, 10); line(m, [(24, 52), (52, 52)]); line(m, [(60, 52), (72, 52)]); line(m, [(24, 64), (40, 64)]); line(m, [(48, 64), (72, 64)]); save('hts_subtitles', m)
# search
m = new(); ring(m, 40, 40, 24); line(m, [(58, 58), (82, 82)], SW + 1); save('hts_search', m)
# block (shield)
s = new(); d = ImageDraw.Draw(s)
shield = [(48, 8), (82, 20), (82, 46)]
for i in range(0, 21):
    a = i / 20
    shield.append((82 - 34 * a, 46 + 42 * math.sin(a * math.pi / 2)))
shield += [(14 + 34 * (1 - i / 20), 46 + 42 * math.sin((1 - i / 20) * math.pi / 2)) for i in range(0, 21)]
shield += [(14, 46), (14, 20)]
d.polygon([(P(x), P(y)) for x, y in shield], fill=255)
m = outline(s); line(m, [(34, 48), (44, 58), (64, 38)]); save('hts_block', m)
# dearrow (thumbnail with sparkle = cleaned title/thumb)
m = new(); rrect(m, 10, 22, 70, 74, 9); line(m, [(20, 64), (34, 48), (44, 58), (52, 50), (60, 64)])
for cx, cy, r in ((78, 20, 10),):
    line(m, [(cx, cy - r), (cx, cy + r)], 5); line(m, [(cx - r, cy), (cx + r, cy)], 5)
save('hts_dearrow', m)
# backup (cloud with arrow up)
c = new(); d = ImageDraw.Draw(c)
for cx, cy, r in ((34, 54, 18), (52, 42, 22), (70, 56, 16)): d.ellipse((P(cx - r), P(cy - r), P(cx + r), P(cy + r)), fill=255)
d.rectangle((P(34), P(54), P(70), P(72)), fill=255)
m = outline(c); line(m, [(52, 80), (52, 56)]); line(m, [(42, 64), (52, 54), (62, 64)]); save('hts_backup', m)
# about (info)
m = new(); ring(m, 48, 48, 38); line(m, [(48, 44), (48, 66)]); dot(m, 48, 31, 4.5); save('hts_about', m)
# update (download)
m = new(); ring(m, 48, 48, 38); line(m, [(48, 26), (48, 62)]); line(m, [(34, 50), (48, 64), (62, 50)]); save('hts_update', m)
# advanced (sliders)
m = new()
for y, x in ((26, 60), (48, 34), (70, 54)):
    line(m, [(14, y), (82, y)]); dot(m, x, y, 9)
save('hts_advanced', m)
print('ok')
