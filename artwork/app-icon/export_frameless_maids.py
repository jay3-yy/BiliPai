"""Export the two approved frameless portraits and audit illustrative masks. No Gradle.

Requires Pillow. Image edits live in the PNG masters; this script only sizes and
places those masters, makes tintable exports, and maintains existing aliases.
"""
from pathlib import Path
import hashlib
import json
import math
import re

from PIL import Image, ImageChops, ImageDraw

from export_play_window import DENSITIES, shape_mask, LIGHT as WINDOW_LIGHT, DARK as WINDOW_DARK

ART = Path(__file__).resolve().parent
ROOT = ART.parents[1]
PREFIX = "ic_launcher_blue_snow_maid"
LIGHT = "#FFFFFF"
DARK = "#090A0C"
ALIAS_FILE = ART / "launcher-image-aliases.json"
PORTRAIT_VERTICAL_OFFSET_DP = -2.0
PORTRAIT_SCALE_MULTIPLIER = 0.88

# Preserve the original close portrait framing. Only face, headdress and side
# bows are essential content; original hair/shoulders may follow the OEM mask.
# Coordinates refer to the initial circle-removal masters, not later body edits.
PORTRAIT_LAYOUTS = {
    "tilt": {
        "center": (625, 530),
        "critical": [(600, 103), (840, 125), (990, 245), (1040, 425),
                     (1085, 595), (1055, 720), (920, 805), (760, 878),
                     (580, 842), (432, 735), (350, 650), (180, 637),
                     (280, 405), (285, 300), (420, 155)],
    },
    "front": {
        "center": (656, 480),
        "critical": [(545, 12), (775, 35), (970, 170), (1050, 340),
                     (1110, 500), (1085, 685), (945, 780), (785, 855),
                     (650, 890), (500, 855), (355, 770), (205, 665),
                     (190, 430), (275, 240), (420, 80)],
    },
}


def fit_layer(path, monochrome=False, composition=None):
    source = Image.open(path).convert("RGBA")
    alpha = source.getchannel("A")
    bounds = alpha.point(lambda a: 255 if a > 16 else 0).getbbox()
    center = composition["center"] if composition else ((bounds[0]+bounds[2])/2, (bounds[1]+bounds[3])/2)
    if composition:
        radius = max(math.hypot(x-center[0], y-center[1]) for x, y in composition["critical"])
    else:
        radius = max(math.hypot(x-center[0], y-center[1])
                     for y in range(bounds[1], bounds[3])
                     for x in range(bounds[0], bounds[2]) if alpha.getpixel((x, y)) > 16)
    # A small shared optical adjustment gives both portraits more balanced
    # headroom while lifting the face. The original artwork stays unchanged.
    scale = 32.5/radius * (PORTRAIT_SCALE_MULTIPLIER if composition else 1)
    offset_y = PORTRAIT_VERTICAL_OFFSET_DP if composition else 0
    if monochrome:
        source = Image.new("RGBA", source.size, "white")
        source.putalpha(alpha)

    critical = Image.new("L", source.size)
    if composition:
        ImageDraw.Draw(critical).polygon(composition["critical"], fill=255)
        critical = ImageChops.multiply(critical, alpha)
    else:
        critical = alpha

    def place(image, size):
        resized = image.resize((round(source.width*scale*size/108),
                                round(source.height*scale*size/108)), Image.Resampling.LANCZOS)
        layer = Image.new("RGBA", (size, size))
        layer.alpha_composite(resized, (round(size/2-center[0]*scale*size/108),
                                       round(size/2-center[1]*scale*size/108+offset_y*size/108)))
        return layer

    def render(size):
        return place(source, size)

    def render_critical(size):
        image = Image.new("RGBA", source.size, "white")
        image.putalpha(critical)
        return place(image, size).getchannel("A")

    critical_radius = max(math.hypot((x-center[0])*scale, (y-center[1])*scale+offset_y)
                          for x, y in composition["critical"]) if composition else 32.5
    return render, {"source": path.name, "criticalRadiusDp": critical_radius,
                    "widthDp": (bounds[2]-bounds[0])*scale,
                    "heightDp": (bounds[3]-bounds[1])*scale,
                    "scale": scale, "center": center, "verticalOffsetDp": offset_y,
                    "scaleMultiplier": PORTRAIT_SCALE_MULTIPLIER if composition else 1,
                    "composition": "original-close-portrait" if composition else "monochrome-safe-zone"}, render_critical


def export():
    layers = {}
    critical_layers = {}
    geometry = {}
    for variant, color, mono in [
        ("tilt", "blue_snow_maid_frameless_foreground.png", "blue_snow_maid_monochrome_4096.png"),
        ("front", "blue_snow_maid_front_frameless_foreground.png", "blue_snow_maid_front_monochrome_4096.png"),
    ]:
        for role, source in [("foreground", color), ("monochrome", mono)]:
            renderer, metrics, critical_renderer = fit_layer(
                ART/source, role == "monochrome", PORTRAIT_LAYOUTS[variant] if role == "foreground" else None)
            layers[variant, role] = renderer
            critical_layers[variant, role] = critical_renderer
            geometry[f"{variant}/{role}"] = metrics

    selected = set()
    for face in ["", "_front"]:
        for appearance in ["", "_light", "_dark"]:
            base = PREFIX+face+appearance
            selected.update([base, base+"_round", base+"_foreground"])
        selected.add(PREFIX+face+"_monochrome")
    aliases = json.loads(ALIAS_FILE.read_text())
    by_original = {entry["original"]: entry for entry in aliases}
    originals = {entry["original"] for entry in aliases if Path(entry["original"]).stem in selected}
    for module in ["app", "brand-motion"]:
        originals.update(str(p.relative_to(ROOT)) for p in (ROOT/module/"src/main/res").glob("mipmap*/*.png")
                         if p.stem in selected)

    old_targets = set()
    for original in sorted(originals):
        path = ROOT/original
        module = Path(original).parts[0]
        qualifier = path.parent.name
        density = next(d for d in DENSITIES if qualifier.endswith("-"+d))
        density_scale = DENSITIES[density]
        name = path.stem
        variant = "front" if name.startswith(PREFIX+"_front") else "tilt"
        dark = "_dark" in name or ("_light" not in name and "-night-" in qualifier)
        if name.endswith("_foreground") or name.endswith("_monochrome"):
            role = "monochrome" if name.endswith("_monochrome") else "foreground"
            image = layers[variant, role](round(108*density_scale))
            canonical = f"ic_launcher_frameless_{variant}_{role}"
        else:
            round_icon = name.endswith("_round")
            canonical = f"ic_launcher_frameless_{variant}_{'dark' if dark else 'light'}_{'round' if round_icon else 'legacy'}"
            size = round(48*density_scale)
            foreground = layers[variant, "foreground"](432).crop((72, 72, 360, 360))
            foreground = foreground.resize((size, size), Image.Resampling.LANCZOS)
            image = Image.new("RGBA", (size, size), DARK if dark else LIGHT)
            image.alpha_composite(foreground)
            if round_icon:
                image.putalpha(shape_mask("circle", size))
        entry = by_original.get(original)
        if entry is None:
            image.save(path, optimize=True)
        else:
            target = ROOT/module/"src/main/res"/f"mipmap-{density}"/(canonical+".png")
            target.parent.mkdir(parents=True, exist_ok=True)
            image.save(target, optimize=True)
            old_targets.add(entry["target"])
            entry["target"] = str(target.relative_to(ROOT))
            entry["sha256"] = hashlib.sha256(target.read_bytes()).hexdigest()
            xml_path = path.parent.parent/qualifier.replace("mipmap", "values", 1)/"launcher_image_aliases.xml"
            xml, count = re.subn(r'(<item type="mipmap" name="'+name+r'">)[^<]+',
                                 r'\g<1>@mipmap/'+canonical, xml_path.read_text())
            assert count == 1, original
            xml_path.write_text(xml)
    ALIAS_FILE.write_text(json.dumps(aliases, indent=2)+"\n")
    # Remove only former shared bitmaps with no remaining XML/alias consumers.
    xml_text = "\n".join(p.read_text() for module in ["app", "brand-motion"]
                         for p in (ROOT/module/"src/main/res").rglob("*.xml"))
    referenced = {entry["target"] for entry in aliases}
    for old in old_targets-referenced:
        if "@mipmap/"+Path(old).stem not in xml_text:
            (ROOT/old).unlink(missing_ok=True)

    # Full-bleed store assets have no pre-baked outer mask or avatar badge.
    for variant, prefix in [("tilt", "blue_snow_maid"), ("front", "blue_snow_maid_front")]:
        foreground = layers[variant, "foreground"](1536).crop((256, 256, 1280, 1280))
        for dark in [False, True]:
            icon = Image.new("RGBA", (1024, 1024), DARK if dark else LIGHT)
            icon.alpha_composite(foreground)
            icon = icon.convert("RGB").resize((512, 512), Image.Resampling.LANCZOS)
            icon.save(ART/f"{prefix}{'_dark' if dark else ''}_playstore_512.png", optimize=True)
            if variant == "tilt" and not dark:
                icon.save(ROOT/"app/src/main/ic_launcher-playstore.png", optimize=True)

    previews = ART/"frameless-previews"
    previews.mkdir(exist_ok=True)
    kinds = ["circle", "rounded-square", "squircle", "teardrop"]
    clipping = {}
    decorative_clipping = {}
    board = Image.new("RGB", (1000, 1040), "#D8D8D8")
    draw = ImageDraw.Draw(board)
    row = 0
    for variant in ["tilt", "front"]:
        for mode, bg, tint in [("light", LIGHT, None), ("dark", DARK, None)]:
            layer = layers[variant, "foreground"](1296).crop((216, 216, 1080, 1080)).resize((200, 200), Image.Resampling.LANCZOS)
            critical = critical_layers[variant, "foreground"](1296).crop((216, 216, 1080, 1080)).resize((200, 200), Image.Resampling.LANCZOS)
            for column, kind in enumerate(kinds):
                image = Image.new("RGBA", (200, 200), bg)
                image.alpha_composite(layer)
                mask = shape_mask(kind, 200)
                image.putalpha(mask)
                board.paste(image, (column*250+25, row*260+35), image)
                draw.text((column*250+20, row*260+12), f"{variant} {mode}: {kind}", fill="#202020")
                solid = layer.getchannel("A").point(lambda a: 255 if a > 32 else 0)
                clipped = ImageChops.multiply(solid, ImageChops.invert(mask))
                decorative_clipping[f"{variant}/{mode}/{kind}"] = sum(clipped.histogram()[1:])
                critical_solid = critical.point(lambda a: 255 if a > 32 else 0)
                clipping[f"{variant}/{mode}/{kind}"] = sum(ImageChops.multiply(critical_solid, ImageChops.invert(mask)).histogram()[1:])
            row += 1
    board.save(previews/"color-masks.png")

    theme_board = Image.new("RGB", (1000, 1040), "#D8D8D8")
    draw = ImageDraw.Draw(theme_board)
    row = 0
    for variant in ["tilt", "front"]:
        for mode, bg, tint in [("light", "#E7E2DA", "#51483C"), ("dark", "#292621", "#D9C5A7")]:
            alpha = layers[variant, "monochrome"](1296).crop((216, 216, 1080, 1080)).getchannel("A").resize((200, 200), Image.Resampling.LANCZOS)
            glyph = Image.new("RGBA", (200, 200), tint)
            glyph.putalpha(alpha)
            for column, kind in enumerate(kinds):
                image = Image.new("RGBA", (200, 200), bg)
                image.alpha_composite(glyph)
                mask = shape_mask(kind, 200)
                image.putalpha(mask)
                theme_board.paste(image, (column*250+25, row*260+35), image)
                draw.text((column*250+20, row*260+12), f"{variant} themed {mode}: {kind}", fill="#202020")
                solid = alpha.point(lambda a: 255 if a > 32 else 0)
                clipping[f"{variant}/themed-{mode}/{kind}"] = sum(ImageChops.multiply(solid, ImageChops.invert(mask)).histogram()[1:])
            row += 1
    theme_board.save(previews/"themed-masks.png")
    small = Image.new("RGB", (620, 530), "#D8D8D8")
    draw = ImageDraw.Draw(small)
    for row, variant in enumerate(["tilt", "front"]):
        layer = layers[variant, "foreground"](1296).crop((216, 216, 1080, 1080))
        for size, x in [(180, 20), (60, 250), (32, 380)]:
            image = Image.new("RGBA", (size, size), LIGHT)
            image.alpha_composite(layer.resize((size, size), Image.Resampling.LANCZOS))
            small.paste(image, (x, row*260+40))
            draw.text((x, row*260+16), f"{variant}: {size}px", fill="#202020")
    small.save(previews/"small-sizes.png")
    # A compact review sheet always reflects the currently exported resources.
    comparison = Image.new("RGB", (960, 570), "#DFDFDF")
    draw = ImageDraw.Draw(comparison)
    window = Image.open(ROOT/"app/src/main/res/mipmap-xxxhdpi/ic_launcher_3d_foreground.png").convert("RGBA")
    examples = [("Play window", window, WINDOW_LIGHT, WINDOW_DARK)]
    examples += [(f"Maid - {variant}", layers[variant, "foreground"](432), LIGHT, DARK)
                 for variant in ["tilt", "front"]]
    for column, (name, layer, light, dark) in enumerate(examples):
        draw.text((column*320+30, 20), name, fill="#242424", font_size=23)
        visible = layer.crop((72, 72, 360, 360)).resize((200, 200), Image.Resampling.LANCZOS)
        for row, background in enumerate([light, dark]):
            image = Image.new("RGBA", (200, 200), background)
            image.alpha_composite(visible)
            image.putalpha(shape_mask("squircle", 200))
            comparison.paste(image, (column*320+60, row*260+65), image)
    comparison.save(ART/"launcher-final-comparison.png")
    report = {"canvasDp": 108, "safeZoneDiameterDp": 66, "geometry": geometry,
              "updatedResourceConfigurations": len(originals), "criticalMaskClippedPixels": clipping,
              "decorativeMaskClippedPixels": decorative_clipping}
    (previews/"export-metrics.json").write_text(json.dumps(report, indent=2)+"\n")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    export()
