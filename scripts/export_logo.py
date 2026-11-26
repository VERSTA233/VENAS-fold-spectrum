#!/usr/bin/env python3
"""Export Android artwork from a PNG or PSD's saved composite (requires Pillow).
Usage: python3 scripts/export_logo.py /path/to/logo.png [--target launcher|splash|all]
The default updates only the launcher/README artwork, preserving the full splash logo.
Splash export additionally requires psd-tools to hide the original lettering groups.
The PSD must be saved with its merged compatibility image. Original stays untouched.
"""
import argparse
import math
from pathlib import Path
from PIL import Image, ImageChops, ImageDraw

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('psd', type=Path)
parser.add_argument('--target', choices=('launcher', 'splash', 'all'), default='launcher')
parser.add_argument('--skip-example', action='store_true', help='Preserve the README logo')
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
with Image.open(args.psd) as document:
    # Frame 0 is the authored composite; subsequent frames are individual layers.
    source = document.convert('RGB')
if source.getpixel((0, 0)) != (0, 0, 0):
    raise ValueError('This export expects the supplied design on a black background')
bounds = source.getbbox()
if bounds is None:
    raise ValueError('Empty logo')
mark = source.crop(bounds)


def artwork_layer(canvas, diagonal, image=source):
    scale = diagonal / math.hypot(mark.width, mark.height)
    size = (round(mark.width * scale), round(mark.height * scale))
    resized = image.crop(bounds).resize(size, Image.Resampling.LANCZOS)
    red, green, blue = resized.split()
    strength = ImageChops.lighter(ImageChops.lighter(red, green), blue)
    foreground = resized.convert('RGBA')
    foreground.putalpha(strength.point(lambda value: 255 if value else 0))
    layer = Image.new('RGBA', (canvas, canvas))
    layer.paste(foreground, ((canvas - size[0]) // 2, (canvas - size[1]) // 2))
    return layer


res = root / 'app/src/main/res/drawable-nodpi'
res.mkdir(parents=True, exist_ok=True)

if args.target in ('splash', 'all'):
    from splash_animation import export_splash_animation, spectrum_from_psd
    spectrum = spectrum_from_psd(args.psd, source)
    # A no-background splash asset uses a 288dp canvas with a 192dp safe circle.
    # Keep the full artwork inside 188dp, including its corners. This is about
    # 10% larger than the launcher foreground, with room for antialiasing.
    splash = artwork_layer(864, 188 * 3)
    base = artwork_layer(864, 188 * 3, spectrum)
    artwork = root / 'scripts/artwork'
    artwork.mkdir(exist_ok=True)
    splash.save(artwork / 'splash_logo.webp', lossless=True, method=6)
    base.save(artwork / 'splash_spectrum.webp', lossless=True, method=6)
    export_splash_animation(splash, base, res, root / 'docs/design')
    print(f'Splash source layers: {artwork}, artwork {splash.getbbox()}')

if args.target in ('launcher', 'all'):
    # Adaptive layers are 108dp; keep the entire mark inside the central
    # 66dp-diameter safe circle. A 64dp diagonal leaves antialiasing room.
    layer = artwork_layer(432, 432 * 64 / 108)
    layer.save(res / 'ic_launcher_foreground.webp', lossless=True, method=6)
    if not args.skip_example:
        example = root / 'example'
        example.mkdir(exist_ok=True)
        source.resize((512, 512), Image.Resampling.LANCZOS).save(example / 'logo.png', optimize=True)
    # Preview Android's central 72dp viewport with common launcher masks.
    viewport = (72, 72, 360, 360)
    visible = Image.alpha_composite(Image.new('RGBA', layer.size, 'black'), layer).crop(viewport)
    sheet = Image.new('RGB', (648, 390), '#e9eaed')
    draw = ImageDraw.Draw(sheet)
    for i, (name, picture, radius) in enumerate([
        ('Rounded square', visible, 64), ('Circle', visible, 144)
    ]):
        mask = Image.new('L', (288, 288))
        ImageDraw.Draw(mask).rounded_rectangle((0, 0, 287, 287), radius=radius, fill=255)
        x = 24 + 312 * i
        sheet.paste(picture.convert('RGB'), (x, 24), mask)
        draw.text((x + 80, 338), name, fill='#20232a')
    preview = root / 'docs/design/logo-preview.png'
    preview.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(preview)
    print(f'Launcher foreground: {layer.getbbox()}; preview: {preview}')
