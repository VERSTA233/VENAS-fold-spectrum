#!/usr/bin/env python3
"""Build a staged splash: spectrum first, then the original PSD lettering.

Usage: python3 scripts/splash_animation.py
Requires Pillow with animated WebP support. PSD extraction also needs psd-tools.
Source layers live in scripts/artwork; only the animation is packaged in the app.
"""
from pathlib import Path

from PIL import Image, ImageDraw

DURATION_MS = 800
BARS_FINISH_MS = 440
LETTERS_START_MS = 480
FRAME_MS = 20
BACKGROUND = '#0E1012'


def spectrum_from_psd(path, original):
    from psd_tools import PSDImage
    document = PSDImage.open(path)
    # These authored groups include the VE / NΛ lettering, masks and clipping layers.
    groups = [layer for layer in document if layer.name in ('组 2', '组 3')]
    if len(groups) != 2:
        raise ValueError('Expected the original VE / NΛ groups (组 2 and 组 3)')
    for layer in groups:
        layer.visible = False
    spectrum = document.composite().convert('RGB')
    # Keep the original rasterized red line; vector rasterizers can round its edges differently.
    red_line = next(layer for layer in document if layer.name == '直线 1')
    left, top, right, bottom = red_line.bbox
    region = (left - 3, top - 3, right + 3, bottom + 3)
    spectrum.paste(original.crop(region), region[:2])
    return spectrum


def ease_out(time_ms, delay_ms, duration_ms):
    progress = max(0., min(1., (time_ms - delay_ms) / duration_ms))
    return 1. - (1. - progress) ** 3


def strips(logo):
    """Split at the dark gaps, retaining all antialiased pixels in each slice."""
    pixels = logo.load()
    runs = []
    start = None
    for x in range(logo.width):
        visible = any(pixels[x, y][3] and max(pixels[x, y][:3]) >= 18
                      for y in range(logo.height))
        if visible and start is None:
            start = x
        elif not visible and start is not None:
            runs.append((start, x))
            start = None
    if start is not None:
        runs.append((start, logo.width))
    if not runs:
        raise ValueError('Empty splash artwork')
    edges = [0] + [(a[1] + b[0]) // 2 for a, b in zip(runs, runs[1:])] + [logo.width]
    result = []
    for left, right in zip(edges, edges[1:]):
        segment = logo.crop((left, 0, right, logo.height))
        bounds = segment.getbbox()
        if bounds:
            x, y = left + bounds[0], bounds[1]
            image = segment.crop(bounds)
            red = any(r > 120 and r > g * 2 and r > b * 2 and a
                      for r, g, b, a in image.getdata())
            result.append((x, y, image, red))
    return result


def frames(logo, spectrum):
    parts = strips(spectrum)
    red_parts = [(x, image) for x, _, image, red in parts if red]
    if len(red_parts) != 1:
        raise ValueError('Expected one central red line in the full splash logo')
    center = red_parts[0][0] + red_parts[0][1].width / 2
    radius = max(abs(x + image.width / 2 - center) for x, _, image, _ in parts)
    output = []
    for time in range(0, DURATION_MS, FRAME_MS):
        if time >= BARS_FINISH_MS:
            # No lettering until every bar has settled, followed by a short quiet beat.
            progress = ease_out(time, LETTERS_START_MS, 280)
            output.append(Image.blend(spectrum, logo, progress))
            continue
        frame = Image.new('RGBA', logo.size)
        for x, y, image, red in parts:
            distance = (x + image.width / 2 - center) / radius
            progress = ease_out(time, 0 if red else 30 + 150 * abs(distance),
                                180 if red else 260)
            height_scale = (.65 + .35 * progress) if red else (.72 + .28 * progress)
            opacity = (.3 + .7 * progress) if red else (.14 + .86 * progress)
            tile = image.resize((image.width, max(1, round(image.height * height_scale))),
                                Image.Resampling.LANCZOS)
            tile.putalpha(tile.getchannel('A').point(lambda a: round(a * opacity)))
            draw_x = x if red else round(center + (x + image.width / 2 - center)
                                        * (.96 + .04 * progress) - image.width / 2)
            draw_y = y + (image.height - tile.height) // 2
            frame.alpha_composite(tile, (draw_x, draw_y))
        output.append(frame)
    # The last frame is the exact full-color logo, without resampling or fading.
    output[-1] = logo.copy()
    return output


def export_splash_animation(logo, spectrum, resource_dir, preview_dir):
    logo = logo.convert('RGBA')
    animation = frames(logo, spectrum.convert('RGBA'))
    target = resource_dir / 'splash_logo_animated.webp'
    # AnimatedImageDrawable streams this one-shot asset; no list of decoded bitmap drawables.
    animation[0].save(target, save_all=True, append_images=animation[1:],
                      duration=FRAME_MS, loop=1, lossless=True, method=6)
    preview_dir.mkdir(parents=True, exist_ok=True)
    preview = []
    inset = logo.width // 6
    for frame in animation:
        viewport = frame.crop((inset, inset, logo.width - inset, logo.height - inset))
        viewport = viewport.resize((384, 384), Image.Resampling.LANCZOS)
        image = Image.new('RGB', (480, 480), BACKGROUND)
        image.paste(viewport, (48, 48), viewport)
        preview.append(image)
    gif = preview_dir / 'splash-logo-animation-staged.gif'
    preview[0].save(gif, save_all=True, append_images=preview[1:], loop=0,
                    duration=[FRAME_MS] * (len(preview) - 1) + [1400], disposal=2)
    sheet = Image.new('RGB', (960, 640), BACKGROUND)
    draw = ImageDraw.Draw(sheet)
    for i, time in enumerate((0, 160, 320, 460, 600, 780)):
        x, y = i % 3 * 320, i // 3 * 320
        sheet.paste(preview[time // FRAME_MS].resize((320, 320), Image.Resampling.LANCZOS), (x, y))
        draw.text((x + 16, y + 12), f'{time} ms', fill='#B6B8BE')
    sheet.save(preview_dir / 'splash-logo-animation-staged-frames.png')
    print(f'One-shot splash: {target} ({target.stat().st_size} bytes); preview: {gif}')


if __name__ == '__main__':
    root = Path(__file__).resolve().parents[1]
    resource_dir = root / 'app/src/main/res/drawable-nodpi'
    artwork = root / 'scripts/artwork'
    with Image.open(artwork / 'splash_logo.webp') as source, \
            Image.open(artwork / 'splash_spectrum.webp') as spectrum:
        export_splash_animation(source, spectrum, resource_dir, root / 'docs/design')
