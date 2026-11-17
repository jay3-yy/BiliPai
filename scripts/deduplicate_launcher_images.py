"""Share byte-identical launcher bitmaps without changing names or qualifiers. No Gradle."""
import hashlib
import json
from collections import defaultdict
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
MODULES = ("app", "brand-motion")
MANIFEST = ROOT / "artwork/app-icon/launcher-image-aliases.json"


def deduplicate():
    groups = defaultdict(list)
    for module in MODULES:
        for path in (ROOT / module / "src/main/res").glob("mipmap*/*.png"):
            if not path.name.startswith("ic_launcher") or path.name.startswith("ic_launcher_shared_"):
                continue
            density = next((q for q in path.parent.name.split("-")[1:] if q.endswith("dpi")), "")
            groups[(hashlib.sha256(path.read_bytes()).hexdigest(), density)].append(path)
    aliases = []
    saved = 0
    outputs = defaultdict(list)
    for (digest, density), paths in sorted(groups.items()):
        if len(paths) < 2:
            continue
        # Shared library resources remain self-contained; app aliases may refer to that library.
        owner = "brand-motion" if any(p.parts[len(ROOT.parts)] == "brand-motion" for p in paths) else "app"
        name = "ic_launcher_shared_" + digest[:16]
        canonical = ROOT / owner / "src/main/res" / ("mipmap-" + density if density else "mipmap") / (name + ".png")
        canonical.parent.mkdir(parents=True, exist_ok=True)
        canonical.write_bytes(paths[0].read_bytes())
        saved += sum(p.stat().st_size for p in paths) - canonical.stat().st_size
        for path in sorted(paths):
            directory = path.parent.parent / path.parent.name.replace("mipmap", "values", 1)
            outputs[directory].append((path.stem, name))
            aliases.append({"original": str(path.relative_to(ROOT)), "target": str(canonical.relative_to(ROOT)), "sha256": digest})
            path.unlink()
    for directory, entries in outputs.items():
        directory.mkdir(parents=True, exist_ok=True)
        target = directory / "launcher_image_aliases.xml"
        resources = ET.parse(target).getroot() if target.exists() else ET.Element("resources")
        for name, canonical in sorted(entries):
            ET.SubElement(resources, "item", {"type": "mipmap", "name": name}).text = "@mipmap/" + canonical
        ET.indent(resources, space="    ")
        target.write_text('<?xml version="1.0" encoding="utf-8"?>\n' + ET.tostring(resources, encoding="unicode") + '\n')
    previous = json.loads(MANIFEST.read_text()) if MANIFEST.exists() else []
    entries = previous + aliases
    targets = {entry["original"]: entry["target"] for entry in entries}
    # 已有别名的目标也可能参与本次去重，直接指向最终图片，避免留下别名链。
    for entry in entries:
        target = entry["target"]
        while target in targets:
            target = targets[target]
        if target == entry["target"]:
            continue
        entry["target"] = target
        original = ROOT / entry["original"]
        xml = original.parent.parent / original.parent.name.replace("mipmap", "values", 1) / "launcher_image_aliases.xml"
        resources = ET.parse(xml).getroot()
        item, = [item for item in resources if item.get("name") == original.stem]
        item.text = "@mipmap/" + Path(target).stem
        ET.indent(resources, space="    ")
        xml.write_text('<?xml version="1.0" encoding="utf-8"?>\n' + ET.tostring(resources, encoding="unicode") + '\n')
    MANIFEST.write_text(json.dumps(entries, indent=2) + "\n")
    print(f"Shared {len(aliases)} launcher entries; source bytes removed: {saved}")


def validate():
    aliases = json.loads(MANIFEST.read_text())
    for entry in aliases:
        original, target = ROOT / entry["original"], ROOT / entry["target"]
        assert not original.exists(), original
        assert hashlib.sha256(target.read_bytes()).hexdigest() == entry["sha256"], target
        xml = original.parent.parent / original.parent.name.replace("mipmap", "values", 1) / "launcher_image_aliases.xml"
        matches = [item for item in ET.parse(xml).getroot() if item.get("name") == original.stem]
        assert len(matches) == 1 and matches[0].text == "@mipmap/" + target.stem, original
    print(f"Validated {len(aliases)} aliases: original qualifiers and exact image bytes preserved")


if __name__ == "__main__":
    import sys
    if "--check" not in sys.argv:
        deduplicate()
    validate()
