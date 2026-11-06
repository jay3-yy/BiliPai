"""Export the approved neutral foreground into Android launcher resources. No Gradle.

Requires Pillow. Run this script from any directory; the repository root is inferred.
The source foreground was separated and recolored using image_gen; exports only
resize/place that approved foreground. Monochrome is a separate native vector.
"""
from pathlib import Path
import hashlib
import json
import math
import re

from PIL import Image, ImageChops, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
ART = Path(__file__).resolve().parent
RES = ROOT / "app/src/main/res"
SOURCE = ART / "bilipai-play-window-neutral-foreground.png"
DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}
LIGHT = "#F3EEE6"
DARK = "#242321"

# The outer contour follows the approved left-leaning speech window. The play
# aperture stays transparent in monochrome so it survives any system tint.
OUTER = [
    ("M", 277, 294), ("L", 915, 218),
    ("C", 1009, 199, 1072, 246, 1086, 342), ("L", 1146, 759),
    ("C", 1160, 856, 1105, 905, 1014, 925), ("L", 1000, 984),
    ("C", 991, 1035, 964, 1053, 923, 1025), ("L", 810, 950),
    ("L", 371, 1007), ("C", 247, 1025, 190, 978, 167, 882),
    ("L", 103, 548), ("C", 73, 408, 135, 315, 277, 294), ("Z",),
]
PLAY = [
    ("M", 478, 498), ("C", 478, 463, 499, 445, 530, 458),
    ("L", 779, 589), ("C", 814, 610, 814, 638, 778, 659),
    ("L", 530, 787), ("C", 502, 808, 478, 790, 478, 756), ("Z",),
]


def path_data(commands):
    return " ".join(command[0] + ",".join(map(str, command[1:])) for command in commands)


def contour_points(commands, scale, center, size):
    points = []
    current = (0, 0)
    for command in commands:
        if command[0] in ("M", "L"):
            current = command[1:]
            points.append(current)
        elif command[0] == "C":
            p0 = current
            p1, p2, p3 = command[1:3], command[3:5], command[5:7]
            for step in range(1, 49):
                t = step / 48
                points.append(tuple(
                    (1-t)**3*p0[i] + 3*(1-t)**2*t*p1[i] + 3*(1-t)*t*t*p2[i] + t**3*p3[i]
                    for i in (0, 1)
                ))
            current = p3
    return [(size/2 + (x-center[0])*scale*size/108,
             size/2 + (y-center[1])*scale*size/108) for x, y in points]


def shape_mask(kind, size):
    image = Image.new("L", (size, size))
    draw = ImageDraw.Draw(image)
    bounds = (0, 0, size-1, size-1)
    if kind == "circle":
        draw.ellipse(bounds, fill=255)
    elif kind == "rounded-square":
        draw.rounded_rectangle(bounds, radius=size*0.23, fill=255)
    elif kind == "teardrop":
        draw.rounded_rectangle(bounds, radius=size*0.5, fill=255)
        draw.rectangle((size/2, size/2, size, size), fill=255)
    else:
        points = []
        for i in range(721):
            angle = i*math.tau/720
            c, s = math.cos(angle), math.sin(angle)
            points.append(((size-1)/2*(1+math.copysign(abs(c)**0.5, c)),
                           (size-1)/2*(1+math.copysign(abs(s)**0.5, s))))
        draw.polygon(points, fill=255)
    return image


def export():
    source = Image.open(SOURCE).convert("RGBA")
    alpha = source.getchannel("A")
    # Export the neutral design with equal RGB channels. This also prevents RGB
    # values in nearly transparent source pixels from tinting resampled edges.
    luminance = source.convert("L")
    source = Image.merge("RGBA", (luminance, luminance, luminance, alpha))
    bounds = alpha.point(lambda value: 255 if value > 16 else 0).getbbox()
    center = ((bounds[0]+bounds[2])/2, (bounds[1]+bounds[3])/2)
    radius = max(math.hypot(x-center[0], y-center[1])
                 for y in range(bounds[1], bounds[3])
                 for x in range(bounds[0], bounds[2]) if alpha.getpixel((x, y)) > 16)
    # 32.5dp radius leaves half a dp inside the official 66dp circular safe zone.
    scale = 32.5/radius

    def foreground(size):
        resized = source.resize((round(source.width*scale*size/108),
                                 round(source.height*scale*size/108)), Image.Resampling.LANCZOS)
        result = Image.new("RGBA", (size, size))
        result.alpha_composite(resized, (round(size/2-center[0]*scale*size/108),
                                        round(size/2-center[1]*scale*size/108)))
        return result

    path = path_data(OUTER) + " " + path_data(PLAY)
    vector = f'''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
    <group android:scaleX="{scale:.8f}" android:scaleY="{scale:.8f}"
        android:translateX="{54-center[0]*scale:.8f}" android:translateY="{54-center[1]*scale:.8f}">
        <path android:fillColor="#FFFFFFFF" android:fillType="evenOdd"
            android:pathData="{path}" />
    </group>
</vector>
'''
    (RES / "drawable/ic_launcher_play_window_monochrome.xml").write_text(vector)
    svg = f'<svg xmlns="http://www.w3.org/2000/svg" width="108" height="108" viewBox="0 0 108 108"><path fill="white" fill-rule="evenodd" transform="translate({54-center[0]*scale} {54-center[1]*scale}) scale({scale})" d="{path}"/></svg>\n'
    (ART / "bilipai-play-window-monochrome.svg").write_text(svg)

    aliases_path = ART / "launcher-image-aliases.json"
    aliases = json.loads(aliases_path.read_text())
    old_targets = set()
    for density, density_scale in DENSITIES.items():
        directory = RES / f"mipmap-{density}"
        foreground(round(108*density_scale)).save(directory / "ic_launcher_3d_foreground.png", optimize=True)
        legacy_size = round(48*density_scale)
        visible = foreground(432).crop((72, 72, 360, 360)).resize((legacy_size, legacy_size), Image.Resampling.LANCZOS)
        legacy = Image.new("RGBA", (legacy_size, legacy_size), LIGHT)
        legacy.alpha_composite(visible)
        for original, target_name, rounded in [
            ("ic_launcher_3d", "ic_launcher_play_window_legacy", False),
            ("ic_launcher_3d_round", "ic_launcher_play_window_round_legacy", True),
        ]:
            target = directory / f"{target_name}.png"
            result = legacy.copy()
            if rounded:
                result.putalpha(shape_mask("circle", legacy_size))
            result.save(target, optimize=True)
            original_path = f"app/src/main/res/mipmap-{density}/{original}.png"
            entry = next(item for item in aliases if item["original"] == original_path)
            old_targets.add(entry["target"])
            entry["target"] = str(target.relative_to(ROOT))
            entry["sha256"] = hashlib.sha256(target.read_bytes()).hexdigest()
        xml_path = RES / f"values-{density}/launcher_image_aliases.xml"
        xml = xml_path.read_text()
        for original, target in [("ic_launcher_3d", "ic_launcher_play_window_legacy"),
                                 ("ic_launcher_3d_round", "ic_launcher_play_window_round_legacy")]:
            xml = re.sub(r'(<item type="mipmap" name="'+original+r'">)[^<]+',
                         r'\g<1>@mipmap/'+target, xml)
        xml_path.write_text(xml)
    aliases_path.write_text(json.dumps(aliases, indent=2)+"\n")
    referenced = {entry["target"] for entry in aliases}
    xml_text = "\n".join(p.read_text() for p in RES.rglob("*.xml"))
    for old in old_targets-referenced:
        if "@mipmap/"+Path(old).stem not in xml_text:
            (ROOT / old).unlink(missing_ok=True)

    preview_dir = ART / "play-window-previews"
    preview_dir.mkdir(exist_ok=True)
    visible = foreground(1296).crop((216, 216, 1080, 1080))
    mono = Image.new("L", (1296, 1296))
    draw = ImageDraw.Draw(mono)
    draw.polygon(contour_points(OUTER, scale, center, 1296), fill=255)
    draw.polygon(contour_points(PLAY, scale, center, 1296), fill=0)
    mono = mono.crop((216, 216, 1080, 1080))
    board = Image.new("RGB", (1000, 1060), "#E0E0E0")
    draw = ImageDraw.Draw(board)
    clipping = {}
    kinds = ["circle", "rounded-square", "squircle", "teardrop"]
    for row, (label, background, tint) in enumerate([
        ("Light", LIGHT, None), ("Dark", DARK, None),
        ("Themed light", "#E7E2DA", "#51483C"), ("Themed dark", "#292621", "#D9C5A7"),
    ]):
        for column, kind in enumerate(kinds):
            size = 200
            color = Image.new("RGBA", (size, size), background)
            if tint:
                glyph = Image.new("RGBA", (size, size), tint)
                glyph.putalpha(mono.resize((size, size), Image.Resampling.LANCZOS))
            else:
                glyph = visible.resize((size, size), Image.Resampling.LANCZOS)
            color.alpha_composite(glyph)
            mask = shape_mask(kind, size)
            color.putalpha(mask)
            board.paste(color, (column*250+25, row*260+38), color)
            draw.text((column*250+25, row*260+15), f"{label}: {kind}", fill="#202020")
            # Check artwork coverage against each illustrative launcher mask.
            solid = glyph.getchannel("A").point(lambda v: 255 if v > 32 else 0)
            clipped = ImageChops.multiply(solid, ImageChops.invert(mask))
            clipping[f"{label}/{kind}"] = sum(clipped.histogram()[1:])
    board.save(preview_dir / "masks-and-themes.png")
    sizes = Image.new("RGB", (560, 270), "#E0E0E0")
    draw = ImageDraw.Draw(sizes)
    for size, x in [(180, 16), (60, 230), (32, 340)]:
        image = Image.new("RGBA", (size, size), LIGHT)
        image.alpha_composite(visible.resize((size, size), Image.Resampling.LANCZOS))
        sizes.paste(image, (x, 40))
        draw.text((x, 16), f"{size}px", fill="#202020")
    sizes.save(preview_dir / "small-sizes.png")
    metrics = {"canvasDp": 108, "safeZoneDiameterDp": 66,
               "foregroundRadiusDp": 32.5, "scale": scale, "sourceCenter": center,
               "palette": {"lightBackground": LIGHT, "darkBackground": DARK, "glyph": "#252525"},
               "illustrativeMaskClippedPixels": clipping}
    (preview_dir / "export-metrics.json").write_text(json.dumps(metrics, indent=2)+"\n")
    print(json.dumps(metrics, indent=2))


if __name__ == "__main__":
    export()
