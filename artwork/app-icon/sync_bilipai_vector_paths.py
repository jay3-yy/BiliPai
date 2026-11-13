"""Sync the BiliPai SVG contour and fill rule into its Android vector resources."""
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
ANDROID = "{http://schemas.android.com/apk/res/android}"
SVG = "{http://www.w3.org/2000/svg}"
source = ET.parse(Path(__file__).with_name("bilipai-mark.svg")).getroot()
source_path, = source.findall(f"{SVG}path")
fill_type = {"evenodd": "evenOdd", "nonzero": "nonZero"}[source_path.attrib["fill-rule"]]
resources = ROOT / "app/src/main/res/drawable"
for target in [*sorted(resources.glob("ic_bilipai_mark*.xml")), resources / "splash_icon_bilipai_monet_foreground.xml"]:
    # Keep each resource's palette, tint and adaptive-icon geometry.
    text = target.read_text()
    root = ET.fromstring(text)
    path, = root.iter("path")
    old_data = path.attrib[f"{ANDROID}pathData"]
    text = text.replace(f'android:pathData="{old_data}"', f'android:pathData="{source_path.attrib["d"]}"')
    if f"{ANDROID}fillType" in path.attrib:
        old_fill_type = path.attrib[f"{ANDROID}fillType"]
        text = text.replace(f'android:fillType="{old_fill_type}"', f'android:fillType="{fill_type}"')
    else:
        text = text.replace('<path ', f'<path android:fillType="{fill_type}" ', 1)
    target.write_text(text)
