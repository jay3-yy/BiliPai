#!/usr/bin/env python3
"""Convert a local Rovniced/bilibili-skin directory or official Suit archive (.zip / .json) into a BiliPai .bpskin.

Supports:
1. Rovniced/bilibili-skin theme directories (<theme>.json + *_package.zip + optional space_bg/ emoji_package/)
2. Official Bilibili Suit archive ZIPs (with GB18030/UTF-8 filenames, data.json, skin/package_url*.zip, card/, card_bg/, space_bg/, emoji_package/)
"""

from __future__ import annotations

import argparse
import io
import json
import re
import zipfile
from pathlib import Path

SOURCE_NAME = "Rovniced/bilibili-skin"
SOURCE_URL = "https://github.com/Rovniced/bilibili-skin"
LICENSE_NOTE = (
    "由用户本地 Rovniced/bilibili-skin 主题或官方装扮存档转换，输出包包含原存档装扮素材；"
    "仅供本地私用或在已获得授权时分享，不要将官方付费主题原图、角色立绘、图标原件或动效资源作为社区包分发。"
)
ICON_MAPPING = {
    "tail_icon_main": "home",
    "tail_icon_dynamic": "following",
    "tail_icon_shop": "member",
    "tail_icon_channel": "channel",
    "tail_icon_myself": "profile",
}
SELECTED_ICON_MAPPING = {
    "tail_icon_selected_main": "home_selected",
    "tail_icon_selected_dynamic": "following_selected",
    "tail_icon_selected_shop": "member_selected",
    "tail_icon_selected_channel": "channel_selected",
    "tail_icon_selected_myself": "profile_selected",
}


def decode_zip_filename(info: zipfile.ZipInfo) -> str:
    if info.flag_bits & 0x800:
        return info.filename
    try:
        return info.filename.encode("cp437").decode("gb18030")
    except Exception:
        return info.filename


def scan_zip_bytes(data: bytes) -> dict[str, bytes]:
    entries: dict[str, bytes] = {}
    with zipfile.ZipFile(io.BytesIO(data)) as zf:
        for info in zf.infolist():
            if info.is_dir():
                continue
            name = decode_zip_filename(info).replace("\\", "/").lstrip("/")
            entries[name] = zf.read(info)
    return entries


def scan_dir_entries(theme_dir: Path) -> dict[str, bytes]:
    entries: dict[str, bytes] = {}
    for file_path in sorted(theme_dir.rglob("*")):
        if file_path.is_file():
            rel = file_path.relative_to(theme_dir).as_posix()
            entries[rel] = file_path.read_bytes()
    return entries


def valid_color(value: object) -> str | None:
    if not isinstance(value, str):
        return None
    raw = value.strip().lstrip("#")
    if re.fullmatch(r"[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?", raw):
        return f"#{raw}"
    return None


def parse_json_obj(raw: bytes) -> dict:
    text = raw.decode("utf-8", errors="replace")
    if "\ufffd" in text:
        text = raw.decode("gb18030", errors="replace")
    parsed = json.loads(text)
    if isinstance(parsed, list) and parsed and isinstance(parsed[0], dict):
        return parsed[0]
    return parsed if isinstance(parsed, dict) else {}


def convert_entries_to_bpskin(outer_entries: dict[str, bytes], skin_id_override: str | None = None, name_override: str | None = None) -> tuple[dict, dict[str, bytes]]:
    json_candidates = [
        (k, v)
        for k, v in outer_entries.items()
        if k.endswith(".json") and Path(k).name not in {"个性装扮-套装.json", "原始.json"}
    ]
    json_candidates.sort(key=lambda kv: (0 if Path(kv[0]).name not in {"data.json", "skin_suit.json", "个性装扮.json"} else 1, kv[0]))

    suit_data = parse_json_obj(outer_entries["data.json"]) if "data.json" in outer_entries else {}
    primary = parse_json_obj(json_candidates[0][1]) if json_candidates else suit_data

    data_node = suit_data.get("data") if isinstance(suit_data.get("data"), dict) else {}
    suit_props = data_node.get("properties") if isinstance(data_node.get("properties"), dict) else {}
    suit_items = data_node.get("suit_items") if isinstance(data_node.get("suit_items"), dict) else {}

    user_equip = primary.get("user_equip") if isinstance(primary.get("user_equip"), dict) else None
    suit_skin = (suit_items.get("skin") or [None])[0] if isinstance(suit_items.get("skin"), list) else None
    theme_node = user_equip or suit_skin or primary
    props = (
        theme_node.get("properties")
        or theme_node.get("data")
        or (primary.get("data") if isinstance(primary.get("data"), dict) else {})
        or {}
    )

    display_name = name_override or str(theme_node.get("name") or data_node.get("name") or primary.get("name") or "Bilibili Skin")
    item_id = str(theme_node.get("item_id") or theme_node.get("id") or data_node.get("item_id") or "theme")
    version = str(theme_node.get("ver") or props.get("ver") or "1.0.0")
    slug = re.sub(r"[^a-z0-9_.-]", "_", item_id.lower()).strip("_") or "theme"
    skin_id = skin_id_override or f"local.bilibili_skin.{slug}"

    pkg_zip_bytes = next(
        (
            v
            for k, v in outer_entries.items()
            if Path(k).name.endswith("_package.zip")
            or (Path(k).name.startswith("package_url") and Path(k).name.endswith(".zip"))
        ),
        None,
    )
    pkg_entries = scan_zip_bytes(pkg_zip_bytes) if pkg_zip_bytes else dict(outer_entries)

    merged: dict[str, bytes] = dict(pkg_entries)
    for k, v in outer_entries.items():
        fname = Path(k).name
        if re.fullmatch(r"image_cover\.(png|jpg|webp)", fname) or (k.startswith("skin/") and re.fullmatch(r"image_(cover|preview)[0-9a-f]{40}\.(png|jpg|webp)", fname)):
            merged.setdefault(f"cover_image.{fname.rsplit('.', 1)[1]}", v)
        elif k.startswith("skin/"):
            m = re.fullmatch(r"([a-z_]+?)[0-9a-f]{40,64}\.(png|jpg)", fname)
            if m:
                merged.setdefault(f"{m.group(1)}.{m.group(2)}", v)
        elif k.startswith("space_bg/"):
            m = re.fullmatch(r"(image[1-9][0-9]*_(?:portrait|landscape))(?:[0-9a-f]{40,64})?\.(png|jpg)", fname)
            if m:
                merged.setdefault(f"space_bg/{m.group(1)}.{m.group(2)}", v)
        elif k.startswith("card/") and re.search(r"\.(png|jpg|webp)$", fname, re.I):
            ext = fname.rsplit(".", 1)[1]
            stem = fname.rsplit(".", 1)[0]
            merged.setdefault(f"{'fan_share_card' if stem.endswith('粉丝') else 'fan_card'}.{ext}", v)
        elif k.startswith("card_bg/") and re.search(r"\.(png|jpg|webp)$", fname, re.I):
            ext = fname.rsplit(".", 1)[1]
            merged.setdefault(f"comment_card_bg.{ext}", v)
        elif k.startswith("emoji_package/") and not fname.endswith("_512x512.webp") and re.search(r"\.(png|webp)$", fname, re.I):
            stem = fname.rsplit(".", 1)[0]
            if stem.startswith("[") and stem.endswith("]"):
                merged.setdefault(f"emoji_package/{fname}", v)

    out_assets: dict[str, bytes] = {}
    manifest_assets: dict[str, object] = {"bottomBarIcons": {}, "spaceBackgrounds": [], "emojiImages": {}}

    def pick(field: str, candidates: list[str]) -> None:
        for cand in candidates:
            hit = next(((p, b) for p, b in merged.items() if Path(p).name == cand), None)
            if hit:
                target = f"assets/{Path(hit[0]).name}"
                out_assets[target] = hit[1]
                manifest_assets[field] = target
                return

    pick("coverImage", ["cover_image.png", "cover_image.jpg", "cover_image.webp"])
    pick("bottomBarTrim", ["tail_bg.png", "tail_bg.jpg"])
    pick("drawerBottomTrim", ["side_bg_bottom.png", "side_bg_bottom.jpg"])
    pick("topAtmosphere", ["head_bg.jpg", "head_bg.png"])
    pick("homeTopTabBackground", ["head_tab_bg.png", "head_tab_bg.jpg"])
    pick("homeSideBackground", ["side_bg.jpg", "side_bg.png"])
    pick("homeProfileBackground", ["head_myself_bg.png", "head_myself_bg.jpg"])
    pick("homeProfileSquaredBackground", ["head_myself_squared_bg.png", "head_myself_squared_bg.jpg"])
    pick("homeProfileVideoBackground", ["head_myself_mp4_bg.mp4"])
    pick("dynamicPublishIcon", ["tail_icon_pub_btn_bg.png", "tail_icon_pub_btn_bg.jpg"])
    pick("dynamicPublishSelectedIcon", ["tail_icon_selected_pub_btn_bg.png", "tail_icon_selected_pub_btn_bg.jpg"])
    pick("fanCardImage", ["fan_card.png", "fan_card.jpg", "fan_card.webp"])
    pick("fanShareCardImage", ["fan_share_card.png", "fan_share_card.jpg", "fan_share_card.webp"])
    pick("commentCardBgImage", ["comment_card_bg.png", "comment_card_bg.jpg", "comment_card_bg.webp"])

    icons_map: dict[str, str] = {}
    for mapping in (ICON_MAPPING, SELECTED_ICON_MAPPING):
        for stem, host_key in mapping.items():
            hit = next(((p, b) for p, b in merged.items() if Path(p).name in {f"{stem}.png", f"{stem}.jpg"}), None)
            if hit:
                target = f"assets/{Path(hit[0]).name}"
                out_assets[target] = hit[1]
                icons_map[host_key] = target
    if icons_map:
        manifest_assets["bottomBarIcons"] = icons_map

    space_groups: dict[int, dict[str, str]] = {}
    emoji_map: dict[str, str] = {}
    for p, b in sorted(merged.items()):
        fname = Path(p).name
        if p.startswith("space_bg/"):
            m = re.fullmatch(r"image([1-9][0-9]*)_(portrait|landscape)\.(png|jpg)", fname)
            if m:
                target = f"assets/space_bg/{fname}"
                out_assets[target] = b
                space_groups.setdefault(int(m.group(1)), {})[m.group(2)] = target
        elif p.startswith("emoji_package/"):
            target = f"assets/emojis/{fname}"
            out_assets[target] = b
            emoji_map[fname.rsplit(".", 1)[0]] = target

    if space_groups:
        manifest_assets["spaceBackgrounds"] = [space_groups[k] for k in sorted(space_groups)]
    if emoji_map:
        manifest_assets["emojiImages"] = emoji_map

    manifest_assets = {k: v for k, v in manifest_assets.items() if v}

    surfaces: list[str] = []
    if "bottomBarTrim" in manifest_assets or icons_map or props.get("tail_color"):
        surfaces.append("HOME_BOTTOM_BAR")
    if "topAtmosphere" in manifest_assets or "homeTopTabBackground" in manifest_assets or props.get("color_second_page"):
        surfaces.append("HOME_TOP_CHROME")
    if "homeSideBackground" in manifest_assets or "drawerBottomTrim" in manifest_assets:
        surfaces.append("HOME_DRAWER")
    if any(k in manifest_assets for k in ("homeProfileBackground", "homeProfileSquaredBackground", "homeProfileVideoBackground", "spaceBackgrounds")):
        surfaces.append("PROFILE")
    if "dynamicPublishIcon" in manifest_assets:
        surfaces.append("DYNAMIC_PUBLISH")
    if any(k in manifest_assets for k in ("commentCardBgImage", "fanCardImage", "fanShareCardImage")):
        surfaces.append("COMMENT_CARD")

    card_fan_color = (((suit_items.get("card") or [{}])[0]).get("properties") or {}).get("fan_no_color") if isinstance(suit_items.get("card"), list) and suit_items.get("card") else None
    card_bg_fan_color = (((suit_items.get("card_bg") or [{}])[0]).get("properties") or {}).get("fan_no_color") if isinstance(suit_items.get("card_bg"), list) and suit_items.get("card_bg") else None

    colors = {
        "coverTint": valid_color(suit_props.get("image_cover_color")),
        "bottomBarTrimTint": valid_color(props.get("tail_color") or props.get("color_second_page")),
        "bottomBarIconTint": valid_color(props.get("tail_icon_color") or props.get("tail_color")),
        "bottomBarIconDarkTint": valid_color(props.get("tail_icon_color_dark")),
        "bottomBarSelectedTint": valid_color(props.get("tail_icon_color_selected") or props.get("tail_color_selected")),
        "bottomBarSelectedDarkTint": valid_color(props.get("tail_icon_color_selected_dark")),
        "topAtmosphereTint": valid_color(props.get("color_second_page") or suit_props.get("image_cover_color") or props.get("color")),
        "topForegroundTint": valid_color(props.get("color")),
        "searchCapsuleTint": valid_color(props.get("color_second_page") or suit_props.get("image_cover_color")),
        "sideBackgroundTint": valid_color(props.get("side_bg_color")),
        "fanNoColor": valid_color(card_fan_color),
        "commentFanNoColor": valid_color(card_bg_fan_color or card_fan_color),
        "playerProgressActiveTint": valid_color(props.get("tail_color_selected")),
        "liquidGlassSurfaceTint": valid_color(props.get("color_second_page") or suit_props.get("image_cover_color") or props.get("tail_color")),
        "liquidGlassTintOpacity": 0.22,
        "colorMode": props.get("color_mode") or None,
    }
    colors = {k: v for k, v in colors.items() if v is not None}

    manifest = {
        "formatVersion": 1,
        "skinId": skin_id,
        "displayName": display_name,
        "version": version,
        "apiVersion": 1,
        "author": "BiliPai local converter",
        "surfaces": surfaces,
        "assets": manifest_assets,
        "colors": colors,
        "motion": {
            "bottomBarIconAnimated": str(props.get("tail_icon_ani") or "").lower() not in {"", "false"},
            "bottomBarIconAnimationMode": props.get("tail_icon_ani_mode") or None,
            "bottomBarIconMode": props.get("tail_icon_mode") or None,
            "profileVideoPlayMode": props.get("head_myself_mp4_play") or None,
        },
        "styleSourceName": SOURCE_NAME,
        "styleSourceUrl": SOURCE_URL,
        "licenseNote": LICENSE_NOTE,
        "communityShareable": False,
        "containsOfficialAssets": True,
    }
    return manifest, out_assets


def main() -> None:
    parser = argparse.ArgumentParser(description="Convert bilibili-skin directory or Suit ZIP to .bpskin")
    parser.add_argument("--input", "--theme-dir", dest="input_path", required=True, type=Path, help="主题目录或官方套装 .zip 路径")
    parser.add_argument("--output", required=True, type=Path, help="输出 .bpskin 路径")
    parser.add_argument("--skin-id", help="可选 skinId")
    parser.add_argument("--display-name", help="可选展示名称")
    args = parser.parse_args()

    src = args.input_path.resolve()
    if src.is_file() and src.suffix.lower() == ".zip":
        outer = scan_zip_bytes(src.read_bytes())
    elif src.is_dir():
        outer = scan_dir_entries(src)
    else:
        raise SystemExit(f"输入路径不存在或格式不受支持: {src}")

    manifest, assets = convert_entries_to_bpskin(outer, args.skin_id, args.display_name)
    out = args.output.resolve()
    out.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(out, "w", compression=zipfile.ZIP_DEFLATED) as zf:
        zf.writestr("skin-manifest.json", json.dumps(manifest, ensure_ascii=False, indent=2))
        for rel_path, content in assets.items():
            zf.writestr(rel_path, content)
    print(f"OK: {out} (surfaces={manifest['surfaces']}, assets={len(assets)})")


if __name__ == "__main__":
    main()
