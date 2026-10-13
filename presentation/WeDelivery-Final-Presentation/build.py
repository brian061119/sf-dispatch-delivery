# -*- coding: utf-8 -*-
"""
WeDelivery final defense deck builder.

Authored in a 1280x720 px coordinate space (1px = 9525 EMU @96 dpi) following
DESIGN.md / STORY.md in this folder.

Key safeguard: every block of body copy is measured with real font metrics and
auto-shrunk until it fits its box, so no paragraph can spill into its neighbour.
"""
import os
import math
from lxml import etree
from PIL import Image, ImageFont
from pptx import Presentation
from pptx.util import Emu, Pt
from pptx.dml.color import RGBColor
from pptx.enum.text import PP_ALIGN, MSO_ANCHOR
from pptx.enum.shapes import MSO_SHAPE
from pptx.oxml.ns import qn

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, 'assets')
CACHE = os.path.join(ASSETS, '_built')
OUT = os.path.join(HERE, 'WeDelivery-Final-Presentation.pptx')
os.makedirs(CACHE, exist_ok=True)

BG, SURF, SURF2 = '0B1220', '16243A', '1E2F49'
PRIM, ACC = '3B82F6', '22D3EE'
TX, MU, DIM = 'F1F5F9', '94A3B8', '64748B'
DANGER, OK = 'F87171', '34D399'
BODY, DISP, CODE = 'Segoe UI', 'Segoe UI', 'Consolas'

PAD_L = 72
CONTENT_W = 1280 - PAD_L * 2          # 1136
TOTAL = 14

_FONT_PATHS = {'segoe ui': 'C:/Windows/Fonts/segoeui.ttf',
               'consolas': 'C:/Windows/Fonts/consola.ttf'}
_fcache = {}


def E(px):
    return Emu(int(round(px * 9525)))


def _font(name, pt, bold):
    key = (name, pt, bold)
    if key in _fcache:
        return _fcache[key]
    path = _FONT_PATHS.get((name or BODY).lower(), _FONT_PATHS['segoe ui'])
    if bold and (name or '').lower() == 'segoe ui':
        path = 'C:/Windows/Fonts/segoeuib.ttf'
    try:
        f = ImageFont.truetype(path, int(round(pt * 96.0 / 72.0)))
    except Exception:
        f = ImageFont.load_default()
    _fcache[key] = f
    return f


def est_h(text, size, width_px, ls=1.2, font=BODY, bold=False):
    """Estimated rendered height (px) of `text` wrapped inside `width_px`."""
    if not text:
        return 0.0
    f = _font(font, size, bold)
    lines, cur = 1, 0.0
    for i, w in enumerate(text.split(' ')):
        piece = w if i == 0 else ' ' + w
        ww = f.getlength(piece)
        if cur > 0 and cur + ww > width_px:
            lines += 1
            cur = f.getlength(w)
        else:
            cur += ww
    return lines * size * 96.0 / 72.0 * 1.2 * ls


def fit_size(text, size, width_px, height_px, ls=1.2, font=BODY, bold=False, floor=8.5):
    s = size
    while s > floor and est_h(text, s, width_px, ls, font, bold) > height_px:
        s -= 0.5
    return s


# ---------------------------------------------------------------- primitives
def noline(shp):
    shp.line.fill.background()


def solid(shp, hexcolor):
    shp.fill.solid()
    shp.fill.fore_color.rgb = RGBColor.from_string(hexcolor)


def set_alpha(shp, pct):
    srgb = shp._element.find('.//' + qn('a:solidFill') + '/' + qn('a:srgbClr'))
    if srgb is None:
        return
    for a in srgb.findall(qn('a:alpha')):
        srgb.remove(a)
    a = etree.SubElement(srgb, qn('a:alpha'))
    a.set('val', str(int(pct * 1000)))


def rect(sl, x, y, w, h, fill=None, line=None, lw=1.0, radius=None, alpha=None):
    st = MSO_SHAPE.ROUNDED_RECTANGLE if radius else MSO_SHAPE.RECTANGLE
    shp = sl.shapes.add_shape(st, E(x), E(y), E(w), E(h))
    if radius is not None:
        try:
            shp.adjustments[0] = radius
        except Exception:
            pass
    if fill:
        solid(shp, fill)
        if alpha is not None:
            set_alpha(shp, alpha)
    else:
        shp.fill.background()
    if line:
        shp.line.color.rgb = RGBColor.from_string(line)
        shp.line.width = Pt(lw)
    else:
        noline(shp)
    shp.shadow.inherit = False
    tf = shp.text_frame
    tf.word_wrap = True
    tf.margin_left = tf.margin_right = tf.margin_top = tf.margin_bottom = 0
    tf.vertical_anchor = MSO_ANCHOR.TOP
    return shp


def tbox(sl, x, y, w, h, anchor=MSO_ANCHOR.TOP):
    tb = sl.shapes.add_textbox(E(x), E(y), E(w), E(h))
    tf = tb.text_frame
    tf.word_wrap = True
    tf.vertical_anchor = anchor
    tf.margin_left = tf.margin_right = tf.margin_top = tf.margin_bottom = 0
    return tf


def para(tf, text, size=16, color=TX, bold=False, italic=False, font=BODY,
         align=PP_ALIGN.LEFT, space_after=0, space_before=0, line_spacing=1.2,
         first=False, spc=None):
    p = tf.paragraphs[0] if first else tf.add_paragraph()
    p.alignment = align
    p.space_after = Pt(space_after)
    p.space_before = Pt(space_before)
    if line_spacing:
        p.line_spacing = line_spacing
    r = p.add_run()
    r.text = text
    r.font.size = Pt(size)
    r.font.bold = bold
    r.font.italic = italic
    r.font.name = font
    r.font.color.rgb = RGBColor.from_string(color)
    if spc is not None:
        r._r.get_or_add_rPr().set('spc', str(int(spc * 100)))
    return r


def fit_para(tf, text, w, h, size, ls=1.45, font=BODY, bold=False, color=MU,
             align=PP_ALIGN.LEFT, first=False, space_before=0, floor=8.5):
    """Write `text`, shrinking the font until it measurably fits w x h."""
    s = fit_size(text, size, w, h, ls, font, bold, floor)
    para(tf, text, size=s, color=color, bold=bold, font=font, align=align,
         line_spacing=ls, first=first, space_before=space_before)
    return s


def fit_image(name, w_px, h_px):
    tag = '%s_%dx%d.png' % (os.path.splitext(name)[0], int(w_px), int(h_px))
    dst = os.path.join(CACHE, tag)
    if not os.path.exists(dst):
        im = Image.open(os.path.join(ASSETS, name)).convert('RGB')
        sw, sh = im.size
        target = w_px / float(h_px)
        if sw / float(sh) > target:
            nw = int(sh * target)
            im = im.crop(((sw - nw) // 2, 0, (sw - nw) // 2 + nw, sh))
        else:
            nh = int(sw / target)
            im = im.crop((0, (sh - nh) // 2, sw, (sh - nh) // 2 + nh))
        im.resize((int(w_px), int(h_px)), Image.LANCZOS).save(dst)
    return dst


def pic(sl, name, x, y, w, h):
    return sl.shapes.add_picture(fit_image(name, w, h), E(x), E(y), E(w), E(h))


def arrow_down(sl, cx, y, h, color=DIM, w=3):
    rect(sl, cx - w / 2.0, y, w, h, fill=color)


def arrow_right(sl, x, cy, w, color=DIM, h=3):
    rect(sl, x, cy - h / 2.0, w, h, fill=color)


def chrome(sl, n, hero=False, title=None):
    rect(sl, 0, 0, 1280, 720, fill=BG)
    if title:
        if hero:
            rect(sl, PAD_L, 26, 44, 4, fill=ACC)
        tf = tbox(sl, PAD_L, 44, CONTENT_W, 68)
        para(tf, title, size=34, bold=True, color=TX, first=True)
    tf = tbox(sl, PAD_L, 668, 500, 24)
    para(tf, 'WeDelivery · Flag Camp', size=14, color=DIM, first=True)
    tf = tbox(sl, 900, 668, 308, 24)
    para(tf, '%02d / %d' % (n, TOTAL), size=14, color=DIM,
         align=PP_ALIGN.RIGHT, first=True)


def rail(sl, x, y, w, h, eyebrow, big):
    """Left-hand colour rail used on 04 and 08."""
    r = rect(sl, x, y, w, h, fill=SURF, radius=0.04)
    rect(sl, x, y, 4, h, fill=ACC)
    t = r.text_frame
    t.margin_left = E(28)
    t.margin_top = E(36)
    para(t, eyebrow, size=13, bold=True, color=ACC, first=True, spc=2.5)
    para(t, big, size=36, bold=True, color=TX, space_before=14, line_spacing=1.15)


prs = Presentation()
prs.slide_width = E(1280)
prs.slide_height = E(720)
BLANK = prs.slide_layouts[6]


# ================================================================ 01 cover
def s01():
    sl = prs.slides.add_slide(BLANK)
    rect(sl, 0, 0, 1280, 720, fill=BG)
    pic(sl, 'cover_hero.png', 0, 0, 1280, 720)
    rect(sl, 0, 0, 1280, 720, fill=BG, alpha=58)
    rect(sl, 0, 0, 800, 720, fill=BG, alpha=48)

    tf = tbox(sl, 72, 190, 700, 26)
    para(tf, 'FLAG CAMP · FINAL PROJECT DEFENSE', size=15, bold=True,
         color=ACC, first=True, spc=3.5)

    tf = tbox(sl, 72, 226, 720, 130)
    para(tf, 'WeDelivery', size=fit_size('WeDelivery', 78, 720, 130, 1.05, DISP, True),
         bold=True, color='FFFFFF', font=DISP, first=True, line_spacing=1.05)

    rect(sl, 72, 356, 132, 5, fill=ACC)

    tf = tbox(sl, 72, 378, 700, 116)
    s1 = fit_size('AI-Powered Last-Mile Delivery Dispatch', 33, 700, 58, 1.25, DISP, True)
    s2 = fit_size('across three San Francisco hubs', 33, 700, 58, 1.25, DISP, True)
    s = min(s1, s2)
    para(tf, 'AI-Powered Last-Mile Delivery Dispatch', size=s, bold=True,
         color='E2E8F0', font=DISP, first=True, line_spacing=1.25)
    para(tf, 'across three San Francisco hubs', size=s, bold=True,
         color='E2E8F0', font=DISP, line_spacing=1.25)

    tf = tbox(sl, 72, 512, 620, 76)
    fit_para(tf, 'Autonomous drone & ground-robot dispatch — with a Gemini assistant that '
                 'turns one sentence into a filled order draft.',
             620, 76, 18, ls=1.5, color=MU, first=True)

    tf = tbox(sl, 72, 636, 700, 26)
    para(tf, 'Flag Camp · Team 03', size=16, bold=True, color='E2E8F0', first=True)
    rect(sl, 238, 640, 1, 18, fill='334155')
    tf = tbox(sl, 254, 636, 200, 26)
    para(tf, 'October 2026', size=16, color=MU, first=True)
    rect(sl, 378, 640, 1, 18, fill='334155')
    tf = tbox(sl, 394, 636, 430, 26)
    para(tf, 'Spring Boot · React 19 · Gemini 1.5 Flash', size=16, color=MU, first=True)


# ================================================================ 02 problem
def s02():
    sl = prs.slides.add_slide(BLANK)
    chrome(sl, 2, title='The Last Mile Is the Expensive Mile')
    pic(sl, 'last_mile.png', 72, 140, 680, 500)

    x = 784
    tf = tbox(sl, x, 140, 424, 92)
    para(tf, 'Physics,\nnot averages', size=30, bold=True, color=TX,
         first=True, line_spacing=1.2)

    tf = tbox(sl, x, 240, 424, 120)
    fit_para(tf, 'A drone carries little and flies fast; a robot hauls five times more at a '
                 'third of the speed. Average them and the ETA becomes fiction.',
             424, 120, 16, ls=1.5, color=MU, first=True)

    specs = [('AERIAL DRONE', ACC, '3 kg', 'max payload',
              '0.05 m³ · 45 km/h · 30 min endurance · 4.0% battery/km'),
             ('GROUND ROBOT', PRIM, '15 kg', 'max payload',
              '0.30 m³ · 15 km/h · 240 min endurance · 2.0% battery/km')]
    y = 372
    for label, col, big, biglab, detail in specs:
        card = rect(sl, x, y, 424, 114, fill=SURF, radius=0.05)
        rect(sl, x, y, 4, 114, fill=col)
        t = card.text_frame
        t.margin_left = E(20)
        t.margin_top = E(14)
        para(t, label, size=13, bold=True, color=col, first=True, spc=2.0)
        p = t.add_paragraph()
        p.space_before = Pt(4)
        r = p.add_run(); r.text = big + '  '
        r.font.size = Pt(26); r.font.bold = True; r.font.name = DISP
        r.font.color.rgb = RGBColor.from_string('FFFFFF')
        r2 = p.add_run(); r2.text = biglab
        r2.font.size = Pt(13); r2.font.name = BODY
        r2.font.color.rgb = RGBColor.from_string(MU)
        fit_para(t, detail, 424 - 20 - 12, 22, 12, ls=1.2, color=MU,
                 space_before=2, floor=9)
        y += 126

    tf = tbox(sl, x, 628, 424, 22)
    para(tf, 'Three hubs: Downtown · Sunset · Mission', size=13, color=DIM, first=True)


# ================================================================ 03 why rebuild
def s03():
    sl = prs.slides.add_slide(BLANK)
    chrome(sl, 3, title='Why We Rebuilt It')

    rows = [
        ('Dimension', '2022 reference (Leluth/WeDelivery)', 'Our rebuild'),
        ('Backend runtime', 'Spring Framework on an external Tomcat', 'Spring Boot 2.7.18 — embedded container'),
        ('Frontend', 'Create React App (now deprecated)', 'React 19 + Vite 6 — cold start in seconds'),
        ('Database', 'AWS RDS MySQL — provision it before anything boots', 'PostgreSQL + H2 dual profile — clone and run'),
        ('AI layer', 'none', 'Gemini 1.5 Flash assistant with a schema contract'),
        ('Cost to onboard', 'A database instance and a servlet container', 'mvn spring-boot:run  ·  npm run dev'),
        ('Regression safety', 'manual', '144 automated tests, green on every merge'),
    ]
    widths = (260, 420, 456)
    fs = sl.shapes.add_table(len(rows), 3, E(72), E(140), E(1136), E(430))
    tbl = fs.table
    tbl.first_row = False
    tbl.horz_banding = False
    for i, w in enumerate(widths):
        tbl.columns[i].width = E(w)
    for i in range(len(rows)):
        tbl.rows[i].height = E(430.0 / len(rows))

    for ri, row in enumerate(rows):
        for ci, val in enumerate(row):
            cell = tbl.cell(ri, ci)
            cell.fill.solid()
            cell.fill.fore_color.rgb = RGBColor.from_string(
                PRIM if ri == 0 else (SURF if ri % 2 else BG))
            cell.margin_left = E(16); cell.margin_right = E(12)
            cell.margin_top = E(4); cell.margin_bottom = E(4)
            cell.vertical_anchor = MSO_ANCHOR.MIDDLE
            tf = cell.text_frame
            tf.word_wrap = True
            w = widths[ci]
            base = 15 if ri == 0 else 14
            size = fit_size(val, base, w - 28, 430.0 / len(rows) - 8, 1.15, BODY,
                            ri == 0 or ci == 0, floor=9.5)
            r = tf.paragraphs[0].add_run()
            r.text = val
            r.font.name = BODY
            r.font.size = Pt(size)
            r.font.bold = (ri == 0 or ci == 0)
            r.font.color.rgb = RGBColor.from_string(
                'FFFFFF' if ri == 0 else (ACC if ci == 0 else (TX if ci == 2 else MU)))

    box = rect(sl, 72, 596, 1136, 60, fill=SURF, radius=0.12)
    rect(sl, 72, 596, 4, 60, fill=ACC)
    t = box.text_frame
    t.margin_left = E(24)
    t.vertical_anchor = MSO_ANCHOR.MIDDLE
    fit_para(t, 'Four years is long enough for a stack to stop being runnable. '
                'That gap is the reason this project exists.',
             1112, 58, 16, ls=1.35, color=TX, first=True)


# ================================================================ 04 why web app
def s04():
    sl = prs.slides.add_slide(BLANK)
    chrome(sl, 4, title='Solution · Why a Web App')
    rail(sl, 72, 140, 300, 516, 'THE SHAPE', 'Web\nApplication')
    rect(sl, 100, 308, 60, 3, fill=PRIM)
    tf = tbox(sl, 100, 326, 236, 200)
    fit_para(tf, 'One codebase, every screen size, shipped as a URL.',
             236, 200, 15, ls=1.5, color=MU, first=True)

    blocks = [
        ('Zero install, zero review',
         'No app-store submission, no version skew between users. A URL is the entire '
         'distribution channel: a fix ships the moment it merges.'),
        ('Dispatch is information-dense',
         'Comparing three delivery options while watching a fleet needs horizontal room. A '
         'laptop shows the map, the price matrix and the vehicle state at once.'),
        ('The map is the product',
         'Route playback on Leaflet is the moment the demo lands — and it is also the view a '
         'real operator would keep open all day.'),
    ]
    x, w, y = 412, 796, 140
    for i, (h, b) in enumerate(blocks):
        rect(sl, x, y, 4, 144, fill=ACC if i == 0 else PRIM)
        tf = tbox(sl, x + 22, y, w - 22, 44)
        para(tf, '0%d   %s' % (i + 1, h), size=22, bold=True, color=TX, first=True)
        tf = tbox(sl, x + 22, y + 48, w - 22, 104)
        fit_para(tf, b, w - 22, 104, 15.5, ls=1.5, color=MU, first=True)
        y += 172

    tf = tbox(sl, x, 640, 796, 22)
    para(tf, 'Responsive to tablet width; the tracking view is the only screen tuned for phones.',
         size=13.5, color=DIM, first=True)


# ================================================================ 05 what it does
def s05():
    sl = prs.slides.add_slide(BLANK)
    chrome(sl, 5, title='Solution · What It Does')
    pic(sl, 'fleet_ops.png', 72, 140, 1136, 292)

    cards = [
        ('Quote', PRIM, False,
         'Two addresses and a parcel in; three priced options out — Drone, Ground Robot or '
         'Off-Peak — built from real distance and battery math.'),
        ('Pay', PRIM, False,
         'Mock card gateway. Any card ending in 0000 fails on purpose, so the rollback path '
         'is demonstrable rather than merely claimed.'),
        ('Track', PRIM, False,
         'Live map, stage-by-stage progress and the full milestone history read straight out '
         'of tracking_events.'),
        ('Ask', ACC, True,
         'A Gemini assistant that reads one sentence and hands back a filled draft, a live '
         'quote, or a tracking card.'),
    ]
    gap = 20
    cw = (1136 - gap * 3) / 4.0
    x, y, ch = 72, 452, 200
    for title, col, hl, body in cards:
        card = rect(sl, x, y, cw, ch, fill='123049' if hl else SURF, radius=0.045)
        rect(sl, x, y, cw, 4, fill=col)
        t = card.text_frame
        t.margin_left = E(20); t.margin_right = E(16); t.margin_top = E(16)
        para(t, title.upper(), size=18, bold=True, color=col, first=True, spc=1.5)
        fit_para(t, body, cw - 36, 102, 13, ls=1.4, color=MU, space_before=10)
        x += cw + gap


# ================================================================ 06 frontend
def s06():
    sl = prs.slides.add_slide(BLANK)
    chrome(sl, 6, title='Design · Frontend')
    pic(sl, 'ui_register.png', 72, 140, 640, 516)

    x, w = 744, 464
    tf = tbox(sl, x, 140, w, 64)
    para(tf, 'React 19  ·  Vite 6  ·  Ant Design 6', size=18, bold=True, color=ACC, first=True)
    para(tf, 'Leaflet  ·  Zustand 5', size=18, bold=True, color=ACC)

    items = [
        ('Zustand over Redux',
         'A four-step wizard plus a chat buffer is about forty lines of state. Zustand gives '
         'us that without actions, reducers and a provider tree.'),
        ('Leaflet over Google Maps',
         'No API key, no quota, no billing surprise, and it renders on cold conference wifi. '
         'We gave up tile styling.'),
        ('One wizard store, four steps',
         'Address → parcel → options → payment all live in one store, which is exactly why the '
         'assistant can pre-fill any step.'),
        ('Assistant above every screen',
         'Mounted once in App.jsx and hidden only on admin routes — the floating button in the '
         'screenshot is always live.'),
    ]
    y = 214
    for h, b in items:
        rect(sl, x, y + 4, 3, 90, fill=PRIM)
        tf = tbox(sl, x + 16, y, w - 16, 28)
        para(tf, h, size=17, bold=True, color=TX, first=True)
        tf = tbox(sl, x + 16, y + 30, w - 16, 74)
        fit_para(tf, b, w - 16, 74, 13.5, ls=1.38, color=MU, first=True)
        y += 108


# ================================================================ 07 architecture
def s07():
    sl = prs.slides.add_slide(BLANK)
    chrome(sl, 7, hero=True, title='Design · Backend Architecture')

    bands = [
        ('CLIENT', 'React 19 · Vite · Leaflet · Zustand', TX, '334155'),
        ('CONTROLLER', 'REST · JWT filter · request validation', PRIM, PRIM),
        ('SERVICE', 'Recommendation · Order · Tracking · Simulation', PRIM, PRIM),
        ('PERSISTENCE', 'Spring Data JPA · @Transactional · row locks', PRIM, PRIM),
        ('DATA', 'PostgreSQL (prod)  ·  H2 (dev & test)', ACC, ACC),
    ]
    bx, bw, bh, gap = 72, 500, 72, 14
    y = 150
    for i, (title, sub, tc, stripe) in enumerate(bands):
        b = rect(sl, bx, y, bw, bh, fill=SURF, radius=0.08)
        rect(sl, bx, y, 5, bh, fill=stripe)
        t = b.text_frame
        t.margin_left = E(22)
        t.vertical_anchor = MSO_ANCHOR.MIDDLE
        para(t, title, size=13.5, bold=True, color=tc, first=True, spc=2.2)
        fit_para(t, sub, bw - 30, 22, 13, ls=1.2, color=MU, floor=9.5)
        if i < len(bands) - 1:
            arrow_down(sl, bx + bw / 2.0, y + bh, gap, DIM, 3)
        y += bh + gap

    ax, ay, aw = 596, 250, 214
    a = rect(sl, ax, ay, aw, 190, fill='123049', radius=0.05)
    rect(sl, ax, ay, aw, 4, fill=ACC)
    t = a.text_frame
    t.margin_left = E(18); t.margin_top = E(16)
    para(t, 'AI BRANCH', size=12, bold=True, color=ACC, first=True, spc=2.0)
    para(t, 'AiDeliveryService', size=14, bold=True, color=TX, space_before=10)
    para(t, 'routes 5 intents', size=12, color=MU)
    para(t, 'GeminiDeliveryClient', size=13, bold=True, color=TX, space_before=10)
    fit_para(t, 'JSON schema · 45 s timeout · 3 retries', aw - 30, 34, 11.5,
             ls=1.2, color=MU, floor=9)
    para(t, 'NO DIRECT DB ACCESS', size=11, bold=True, color=DANGER,
         space_before=8, spc=1.0)
    arrow_right(sl, bx + bw, 336, ax - (bx + bw), ACC, 3)

    nx, nw = 830, 378
    tf = tbox(sl, nx, 150, nw, 112)
    para(tf, 'Four layers,\none direction', size=28, bold=True, color=TX,
         first=True, line_spacing=1.2)

    notes = [
        'Controllers validate and authorise. They hold no business logic.',
        'Services own every rule: pricing, assignment, cancellation, refunds.',
        'JPA maps eight tables; PostgreSQL in production, H2 in tests.',
        'The AI branch is read-mostly: it calls the same services a human would, and never '
        'writes on its own.',
    ]
    y = 264
    for n in notes:
        rect(sl, nx, y + 6, 6, 6, fill=PRIM)
        tf = tbox(sl, nx + 20, y, nw - 20, 80)
        fit_para(tf, n, nw - 20, 80, 14.5, ls=1.42, color=MU, first=True)
        y += 86

    box = rect(sl, nx, 606, nw, 50, fill=SURF, radius=0.12)
    rect(sl, nx, 606, 4, 50, fill=ACC)
    t = box.text_frame
    t.margin_left = E(18)
    t.vertical_anchor = MSO_ANCHOR.MIDDLE
    fit_para(t, 'The model never invents a price — it calls RecommendationService.',
             nw - 32, 46, 13.5, ls=1.25, color=ACC, first=True, floor=10)


# ================================================================ 08 stack
def s08():
    sl = prs.slides.add_slide(BLANK)
    chrome(sl, 8, title='Tech Stack')
    rail(sl, 72, 140, 300, 516, 'STACK', 'What we\nactually run')
    rect(sl, 100, 316, 60, 3, fill=PRIM)
    tf = tbox(sl, 100, 334, 236, 220)
    fit_para(tf, 'Chosen for a six-week team of five, not for a benchmark — boring where it '
                 'can be, modern where it pays.',
             236, 220, 15, ls=1.5, color=MU, first=True)

    groups = [
        ('BACKEND', ['Java 11', 'Spring Boot 2.7.18', 'Spring Data JPA', 'PostgreSQL', 'H2',
                     'Maven', 'JUnit 5', 'MockMvc']),
        ('FRONTEND', ['React 19', 'Vite 6', 'Ant Design 6', 'Leaflet', 'Zustand 5',
                      'React Router']),
        ('AI  &  QUALITY', ['Gemini 1.5 Flash', 'Java 11 HttpClient', 'Jackson', 'Mockito',
                            '144 tests', 'H2 test profile']),
    ]
    x0, maxw = 412, 796
    y = 140
    for label, chips in groups:
        tf = tbox(sl, x0, y, maxw, 22)
        para(tf, label, size=13, bold=True, color=ACC, first=True, spc=2.5)
        cx, cy = x0, y + 30
        for c in chips:
            w = max(96, int(len(c) * 9.6 + 34))
            if cx + w > x0 + maxw:
                cx = x0
                cy += 56
            ch = rect(sl, cx, cy, w, 42, fill=SURF2, radius=0.28)
            t = ch.text_frame
            t.vertical_anchor = MSO_ANCHOR.MIDDLE
            para(t, c, size=fit_size(c, 14, w - 16, 42, 1.2, BODY, False, floor=10),
                 color=TX, align=PP_ALIGN.CENTER, first=True)
            cx += w + 12
        y = cy + 42 + 46

    tf = tbox(sl, 412, 634, 796, 22)
    para(tf, 'The AI layer added zero new Maven dependencies — HttpClient + Jackson is enough.',
         size=13.5, color=DIM, first=True)


# ================================================================ 09 trade-offs
def s09():
    sl = prs.slides.add_slide(BLANK)
    chrome(sl, 9, hero=True, title='Trade-offs We Made')

    rows = [
        ('Decision', 'What we rejected', 'What we chose', 'The price we accepted'),
        ('Route distance', 'Mapbox / Google Directions API', 'Haversine × 1.35 Manhattan correction',
         'About 10% off real street distance — isolated behind one RouteService interface'),
        ('Vehicle position', 'WebSocket push, or a scheduled writer', 'On-demand interpolation at request time',
         'No replayable history; milestones are persisted in tracking_events'),
        ('GPS source', 'Real GPS hardware', 'Math derived from elapsed time',
         'Not a real fleet signal — but the demo runs on any machine'),
        ('Payments', 'Stripe / PayPal sandbox', 'Mock gateway; card ending 0000 always fails',
         'No real money flow; in exchange the rollback path is provable'),
        ('Database', 'PostgreSQL only', 'PostgreSQL + H2 dual profile',
         'Two dialects to keep honest — worth it for zero-setup onboarding'),
        ('Maps', 'Google Maps GL', 'Leaflet + OpenStreetMap',
         'Fewer tile styles; no key, no quota, no billing surprise'),
        ('Client state', 'Redux Toolkit / Context API', 'Zustand 5',
         'A smaller middleware ecosystem; wizard plus chat is ~40 lines'),
        ('AI output', 'Function calling', 'responseSchema JSON mode',
         'No chained tool calls — fine, the model may not call tools'),
    ]
    widths = (176, 286, 322, 352)
    fs = sl.shapes.add_table(len(rows), 4, E(72), E(140), E(1136), E(462))
    tbl = fs.table
    tbl.first_row = False
    tbl.horz_banding = False
    for i, w in enumerate(widths):
        tbl.columns[i].width = E(w)
    row_h = 462.0 / len(rows)
    for i in range(len(rows)):
        tbl.rows[i].height = E(row_h)

    for ri, row in enumerate(rows):
        for ci, val in enumerate(row):
            cell = tbl.cell(ri, ci)
            cell.fill.solid()
            cell.fill.fore_color.rgb = RGBColor.from_string(
                PRIM if ri == 0 else (SURF if ri % 2 else BG))
            cell.margin_left = E(12); cell.margin_right = E(10)
            cell.margin_top = E(3); cell.margin_bottom = E(3)
            cell.vertical_anchor = MSO_ANCHOR.MIDDLE
            tf = cell.text_frame
            tf.word_wrap = True
            base = 13 if ri == 0 else 11.5
            size = fit_size(val, base, widths[ci] - 22, row_h - 6, 1.15, BODY,
                            ri == 0 or ci == 0, floor=8.5)
            r = tf.paragraphs[0].add_run()
            r.text = val
            r.font.name = BODY
            r.font.size = Pt(size)
            r.font.bold = (ri == 0 or ci == 0)
            r.font.color.rgb = RGBColor.from_string(
                'FFFFFF' if ri == 0 else (ACC if ci == 0 else (TX if ci == 2 else MU)))

    box = rect(sl, 72, 614, 1136, 42, fill=SURF, radius=0.16)
    rect(sl, 72, 614, 4, 42, fill=ACC)
    t = box.text_frame
    t.margin_left = E(24)
    t.vertical_anchor = MSO_ANCHOR.MIDDLE
    fit_para(t, 'Every row has a price. That is the difference between a stack you chose '
                'and a stack you inherited.',
             1112, 40, 15.5, ls=1.25, color=TX, first=True, floor=11)


# ================================================================ 10 concurrency
def s10():
    sl = prs.slides.add_slide(BLANK)
    chrome(sl, 10, title='Technical Challenge · Concurrency')

    for y in (176, 272):
        b = rect(sl, 96, y, 190, 58, fill=SURF, radius=0.14)
        t = b.text_frame
        t.vertical_anchor = MSO_ANCHOR.MIDDLE
        para(t, 'User A clicks Pay' if y == 176 else 'User B clicks Pay',
             size=fit_size('User A clicks Pay', 14, 174, 58, 1.2, BODY, False, floor=10),
             color=TX, align=PP_ALIGN.CENTER, first=True)
    arrow_right(sl, 286, 205, 74, PRIM, 3)
    arrow_right(sl, 286, 301, 74, PRIM, 3)

    lk = rect(sl, 360, 210, 250, 80, fill='123049', radius=0.1)
    rect(sl, 360, 210, 4, 80, fill=ACC)
    t = lk.text_frame
    t.margin_left = E(18)
    t.vertical_anchor = MSO_ANCHOR.MIDDLE
    para(t, 'SELECT … FOR UPDATE', size=fit_size('SELECT … FOR UPDATE', 16, 228, 50, 1.2, BODY, True, floor=11),
         bold=True, color=ACC, first=True)
    para(t, 'row-level pessimistic lock', size=12, color=MU)
    arrow_down(sl, 485, 290, 34, PRIM, 3)

    tx = rect(sl, 330, 324, 310, 222, fill=SURF, radius=0.05)
    t = tx.text_frame
    t.margin_left = E(20); t.margin_top = E(14)
    para(t, '@Transactional', size=13, bold=True, color=ACC, first=True, spc=1.0)
    para(t, '(rollbackFor = Exception.class)', size=12.5, color=ACC)
    for i, step in enumerate(['01  Lock the vehicle row',
                              '02  Charge the card',
                              '03  Mark vehicle BUSY',
                              '04  Order → PAID']):
        para(t, step, size=13.5, color=TX if i in (1, 3) else MU, space_before=10)

    arrow_down(sl, 485, 546, 30, PRIM, 3)
    cm = rect(sl, 360, 576, 250, 44, fill=PRIM, radius=0.16)
    t = cm.text_frame
    t.vertical_anchor = MSO_ANCHOR.MIDDLE
    para(t, 'COMMIT — all four, or none', size=13.5, bold=True, color='FFFFFF',
         align=PP_ALIGN.CENTER, first=True)
    tf = tbox(sl, 330, 622, 310, 26)
    para(tf, 'card ending 0000  →  ROLLBACK', size=13, bold=True, color=DANGER,
         align=PP_ALIGN.CENTER, first=True)

    x, w = 716, 492
    tf = tbox(sl, x, 140, w, 86)
    para(tf, 'Two people,\none vehicle', size=30, bold=True, color=TX, first=True,
         line_spacing=1.2)
    tf = tbox(sl, x, 232, w, 132)
    fit_para(tf, 'Two customers can hit Pay on the last idle drone in the same second. '
                 'Without a lock both requests read the vehicle as IDLE, both charge a card, '
                 'and one delivery silently loses its vehicle.',
             w, 132, 15.5, ls=1.5, color=MU, first=True)

    tf = tbox(sl, x, 372, w, 20)
    para(tf, 'HOW WE HANDLE IT', size=13, bold=True, color=ACC, first=True, spc=2.5)

    pts = [
        'findByOrderNumberForUpdate takes the row lock before any state changes.',
        'All four steps live inside one transaction boundary.',
        'rollbackFor = Exception.class means a checked exception rolls back too.',
        'ownsVehicle() re-checks ownership, so a stale request cannot steer a released vehicle.',
    ]
    y = 392
    for p in pts:
        rect(sl, x, y + 6, 6, 6, fill=PRIM)
        tf = tbox(sl, x + 20, y, w - 20, 56)
        fit_para(tf, p, w - 20, 56, 13.5, ls=1.38, color=MU, first=True)
        y += 56

    box = rect(sl, x, 620, w, 36, fill=SURF, radius=0.2)
    t = box.text_frame
    t.vertical_anchor = MSO_ANCHOR.MIDDLE
    fit_para(t, '0000 card fails → vehicle stays IDLE, no order created.',
             w, 34, 13, ls=1.25, bold=True, color=ACC, align=PP_ALIGN.CENTER,
             first=True, floor=10.5)


# ================================================================ 11 virtual gps
def s11():
    sl = prs.slides.add_slide(BLANK)
    chrome(sl, 11, title='Technical Challenge · Virtual GPS')

    tf = tbox(sl, 72, 150, 400, 150)
    para(tf, '1,862', size=fit_size('1,862', 104, 400, 150, 1.0, DISP, True),
         bold=True, color=ACC, font=DISP, first=True, line_spacing=1.0)
    tf = tbox(sl, 72, 310, 400, 70)
    fit_para(tf, 'coordinate points in a single route polyline', 400, 70, 21,
             ls=1.3, bold=True, color=TX, first=True, floor=14)
    tf = tbox(sl, 72, 392, 400, 80)
    fit_para(tf, 'station → pickup → drop-off → station, concatenated into one path',
             400, 80, 15, ls=1.5, color=MU, first=True)

    x, w = 500, 708
    tf = tbox(sl, x, 146, w, 96)
    para(tf, 'Position is computed,\nnever received', size=29, bold=True, color=TX,
         first=True, line_spacing=1.2)
    tf = tbox(sl, x, 254, w, 100)
    fit_para(tf, 'There is no GPS hardware. The marker is interpolated from elapsed time '
                 'against the polyline, so the vehicle always sits on the road instead of '
                 'cutting across city blocks.',
             w, 100, 16, ls=1.5, color=MU, first=True)

    tf = tbox(sl, x, 366, w, 18)
    para(tf, 'MEASURED LEG SHARE ON A LIVE ORDER', size=12.5, bold=True, color=ACC,
         first=True, spc=2.0)

    legs = [(0.204, PRIM, 'STATION → PICKUP'),
            (0.506, ACC, 'PICKUP → DROP-OFF'),
            (0.290, '1D4ED8', 'DROP-OFF → STATION')]
    by, bh = 388, 70
    rect(sl, x, by, w, bh, fill=SURF, radius=0.06)
    lx = x
    for frac, col, label in legs:
        lw = w * frac
        seg = rect(sl, lx, by, lw, bh, fill=col, alpha=88)
        t = seg.text_frame
        t.vertical_anchor = MSO_ANCHOR.MIDDLE
        para(t, label, size=12.5, bold=True, color='FFFFFF', align=PP_ALIGN.CENTER, first=True)
        para(t, '%.1f%%' % (frac * 100), size=11.5, color='E2E8F0',
             align=PP_ALIGN.CENTER, space_before=2)
        lx += lw

    for i, tk in enumerate(['0:00', 'pickup', 'delivered', 'docked']):
        frac = sum(l[0] for l in legs[:i])
        tx_ = x + w * frac
        if i > 0:
            rect(sl, tx_ - 1, by - 8, 2, bh + 16, fill='FFFFFF', alpha=70)
        tf = tbox(sl, max(x, tx_ - 60), by + bh + 8, 120, 18)
        para(tf, tk, size=12, color=MU,
             align=PP_ALIGN.CENTER if i else PP_ALIGN.LEFT, first=True)

    box = rect(sl, x, 478, w, 178, fill=SURF, radius=0.045)
    rect(sl, x, 478, 4, 178, fill=ACC)
    t = box.text_frame
    t.margin_left = E(22); t.margin_top = E(16)
    para(t, 'THE BUG WE FIXED', size=12.5, bold=True, color=ACC, first=True, spc=2.0)
    fit_para(t, 'Milestone fractions were hardcoded at 0.75 / 1.0, so a vehicle was declared '
                '"back at the station" the instant it delivered — and released while still '
                'down the street.',
             w - 40, 76, 14, ls=1.42, color=MU, space_before=8)
    fit_para(t, 'TripTimeline now derives every fraction from real leg distances, so the '
                'pickup window, the delivery check and the dock release all read one source '
                'of truth.',
             w - 40, 52, 14, ls=1.42, bold=True, color=TX, space_before=8)


# ================================================================ 12 frontend challenge
def s12():
    sl = prs.slides.add_slide(BLANK)
    chrome(sl, 12, title='Technical Challenge · Frontend')

    rect(sl, 72, 140, 600, 516, fill=SURF2, radius=0.03)
    rect(sl, 72, 140, 600, 4, fill=PRIM)

    tf = tbox(sl, 96, 164, 560, 20)
    para(tf, 'TRACKING LOOP', size=12.5, bold=True, color=ACC, first=True, spc=2.2)

    pts = [(120, 244), (196, 214), (272, 258), (348, 220), (424, 250), (500, 226),
           (576, 246), (632, 232)]
    for i in range(len(pts) - 1):
        x1, y1 = pts[i]
        x2, y2 = pts[i + 1]
        ln = sl.shapes.add_shape(
            MSO_SHAPE.RECTANGLE, E(x1), E(y1 - 1.5),
            E(math.hypot(x2 - x1, y2 - y1)), E(3))
        solid(ln, PRIM); noline(ln); ln.shadow.inherit = False
        ln.rotation = math.degrees(math.atan2(y2 - y1, x2 - x1))
    for cx_, cy_ in pts:
        rect(sl, cx_ - 4, cy_ - 4, 8, 8, fill=SURF2, line=PRIM, lw=0.75)
    rect(sl, 413, 239, 22, 22, fill=ACC, radius=0.5)

    tf = tbox(sl, 300, 282, 300, 22)
    para(tf, 'marker = polyline[floor(progress × 1,862)]',
         size=fit_size('marker = polyline[floor(progress × 1,862)]', 13, 300, 22, 1.2, BODY, False, floor=9),
         color=MU, first=True)

    cb = rect(sl, 96, 316, 552, 170, fill=BG, radius=0.03)
    t = cb.text_frame
    t.margin_left = E(20); t.margin_top = E(18)
    para(t, 'setInterval(…, 3000)   // no socket, no writer', size=13, color=DIM,
         font=CODE, first=True)
    para(t, 'const generation = get().generation;', size=13.5, color=ACC, font=CODE,
         space_before=10)
    para(t, 'await send(message);', size=13.5, color=TX, font=CODE, space_before=4)
    para(t, 'if (get().generation !== generation) return;  // stale', size=13.5,
         color=OK, font=CODE, space_before=4)

    tf = tbox(sl, 96, 492, 552, 18)
    para(tf, 'WHAT THE GUARD PREVENTS', size=12.5, bold=True, color=ACC, first=True, spc=2.0)
    tf = tbox(sl, 96, 514, 552, 142)
    fit_para(tf, 'Ask the assistant something, then clear the chat before it answers. '
                 'Without the guard the late reply lands in a conversation that no longer '
                 'exists — or in the next session. Every clear() and logout() bumps the '
                 'counter, so a stale response is dropped.',
             552, 154, 14, ls=1.42, color=MU, first=True)

    x, w = 712, 496
    tf = tbox(sl, x, 140, w, 116)
    para(tf, 'Smooth motion\nwithout a socket', size=29, bold=True, color=TX, first=True,
         line_spacing=1.2)
    tf = tbox(sl, x, 262, w, 130)
    fit_para(tf, 'The map advances by recomputing an index into the polyline every three '
                 'seconds. No WebSocket, no background writer, no server-side simulation — '
                 'so a tracking URL works on any device and costs one cheap query per poll.',
             w, 130, 15.5, ls=1.5, color=MU, first=True)

    rect(sl, x, 404, 60, 3, fill=ACC)
    tf = tbox(sl, x, 414, w, 100)
    para(tf, 'The race nobody\nnotices until it ships', size=25, bold=True, color=ACC,
         first=True, line_spacing=1.2)
    tf = tbox(sl, x, 516, w, 130)
    fit_para(tf, 'It is the same pattern the large chat products use, and the whole fix is '
                 'four lines. We ship it because the failure is invisible in testing and '
                 'embarrassing in a demo.',
             w, 132, 15.5, ls=1.5, color=MU, first=True)


# ================================================================ 13 demo
def s13():
    sl = prs.slides.add_slide(BLANK)
    chrome(sl, 13, title='Demo')
    pic(sl, 'demo_scene.png', 72, 132, 1136, 280)

    cards = [
        ('0:00 – 0:45', 'Place an order',
         'Register, drop two addresses, compare Drone / Robot / Off-Peak, then pay. The 0000 '
         'card fails on purpose.'),
        ('0:45 – 2:30', 'Watch it move',
         'Live tracking: the marker walks the polyline and stages flip from pickup to '
         'drop-off to returning.'),
        ('2:30 – 4:00', 'Ask the assistant',
         'One sentence in, a filled draft or a live quote back. Then the admin console with '
         'a live fleet map.'),
    ]
    gap = 24
    cw = (1136 - gap * 2) / 3.0
    x = 72
    for ts, title, body in cards:
        card = rect(sl, x, 432, cw, 200, fill=SURF, radius=0.045)
        rect(sl, x, 432, cw, 4, fill=PRIM)
        t = card.text_frame
        t.margin_left = E(22); t.margin_right = E(18); t.margin_top = E(16)
        para(t, ts, size=13, bold=True, color=ACC, first=True, spc=2.0)
        para(t, title, size=21, bold=True, color=TX, space_before=6)
        fit_para(t, body, cw - 40, 104, 13, ls=1.4, color=MU, space_before=10)
        x += cw + gap

    tf = tbox(sl, 72, 640, 1136, 22)
    para(tf, 'Pre-recorded, not live — a four-minute walkthrough that cannot be broken by the room.',
         size=13.5, color=DIM, align=PP_ALIGN.CENTER, first=True)


# ================================================================ 14 closing
def s14():
    sl = prs.slides.add_slide(BLANK)
    rect(sl, 0, 0, 1280, 720, fill=BG)
    rect(sl, 0, 0, 1280, 720, fill=PRIM, alpha=6)

    tf = tbox(sl, 0, 146, 1280, 22)
    para(tf, 'SHIPPED', size=14, bold=True, color=ACC, align=PP_ALIGN.CENTER,
         first=True, spc=4.0)
    tf = tbox(sl, 0, 176, 1280, 180)
    para(tf, '144', size=110, bold=True, color=ACC, align=PP_ALIGN.CENTER,
         font=DISP, first=True, line_spacing=1.0)
    tf = tbox(sl, 0, 362, 1280, 44)
    para(tf, 'automated tests · zero failures · reproducible on any machine',
         size=22, color=TX, align=PP_ALIGN.CENTER, first=True)
    rect(sl, 604, 414, 72, 4, fill=ACC)
    tf = tbox(sl, 0, 428, 1280, 76)
    para(tf, 'A stack we chose — and can defend line by line.', size=38, bold=True,
         color='FFFFFF', align=PP_ALIGN.CENTER, first=True, line_spacing=1.2)

    nxt = [('Next: real routing',
            'Swap the Haversine estimate for a road-network API behind the RouteService '
            'interface that already exists.'),
           ('Next: push tracking',
            'Replace three-second polling with WebSocket push once we need sub-second fleet '
            'telemetry.'),
           ('Next: guard the AI bill',
            'Add per-user rate limiting on the AI endpoints — authenticated today, but not '
            'throttled.')]
    gap = 24
    cw = (1136 - gap * 2) / 3.0
    x = 72
    for title, body in nxt:
        card = rect(sl, x, 512, cw, 128, fill=SURF, radius=0.05)
        rect(sl, x, 512, 4, 128, fill=ACC)
        t = card.text_frame
        t.margin_left = E(22); t.margin_right = E(18); t.margin_top = E(18)
        para(t, title, size=17, bold=True, color=TX, first=True)
        fit_para(t, body, cw - 40, 66, 13, ls=1.4, color=MU, space_before=8)
        x += cw + gap

    tf = tbox(sl, 0, 664, 1280, 24)
    para(tf, 'Thank you  ·  Flag Camp Team 03  ·  2026', size=15, color=DIM,
         align=PP_ALIGN.CENTER, first=True)


for fn in (s01, s02, s03, s04, s05, s06, s07, s08, s09, s10, s11, s12, s13, s14):
    fn()

prs.save(OUT)
print('OK ->', OUT)
print('slides =', len(prs.slides._sldIdLst))
