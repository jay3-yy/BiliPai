"""Deduplicate shipped images; keep the artwork preview self-contained and offline."""
import argparse
import base64
import copy
import json
import io
import hashlib
import shutil
import subprocess
from PIL import Image
from pathlib import Path

ART = Path(__file__).resolve().parent
ROOT = ART.parents[1]
RAW = ROOT / 'brand-motion/src/main/res/raw'
DRAWABLE = ROOT / 'brand-motion/src/main/res/drawable-nodpi'
FALLBACKS = {
    'welcome': 'bilipai_maid_static',
    'clean_complete': 'bilipai_maid_clean_static',
    'cleaning': 'bilipai_maid_cleaning_static',
    'retry': 'bilipai_maid_retry_static',
    'empty': 'bilipai_maid_empty_static',
    'search_empty': 'bilipai_maid_search_empty_static',
    'favorite_saved': 'bilipai_maid_favorite_static',
    'follow_success': 'bilipai_maid_follow_static',
    'unfollow_complete': 'bilipai_maid_unfollow_static',
    'dislike_confirmed': 'bilipai_maid_dislike_static',
    'share_ready': 'bilipai_maid_share_static',
    'coin_success': 'bilipai_maid_coin_static',
    'download_complete': 'bilipai_maid_download_static',
    'triple_success': 'bilipai_maid_triple_static',
}

def encode_lossless_webp(png):
    encoder = shutil.which('cwebp')
    if encoder is None:
        raise RuntimeError('Install cwebp for exact lossless image packaging')
    encoded = subprocess.run([encoder, '-quiet', '-lossless', '-exact', '-m', '4', '-o', '-', '--', '-'],
                             input=png, capture_output=True, check=True).stdout
    original = Image.open(io.BytesIO(png)).convert('RGBA')
    decoded = Image.open(io.BytesIO(encoded)).convert('RGBA')
    assert original.size == decoded.size and original.tobytes() == decoded.tobytes(), 'Lossless RGBA mismatch'
    return encoded


def render_preview():
    animations = {}
    for state, fallback in FALLBACKS.items():
        key = 'bilipai_maid_' + state
        data = json.loads((RAW / (key + '.json')).read_text())
        for asset in data['assets']:
            if asset.get('id') == 'maid_bitmap':
                webp = (DRAWABLE / (fallback + '.webp')).read_bytes()
                asset.update(p='data:image/webp;base64,' + base64.b64encode(webp).decode(), u='', e=1)
        animations[key] = data
    return (ART / 'preview-template.html').read_text().replace(
        '__ANIMATIONS__', json.dumps(animations, separators=(',', ':')))

def build_preview():
    (ART / 'preview.html').write_text(render_preview())

def package_existing():
    before = after = 0
    pixel_manifest = {}
    for state, fallback in FALLBACKS.items():
        path = RAW / ('bilipai_maid_' + state + '.json')
        before += path.stat().st_size
        data = json.loads(path.read_text())
        original = copy.deepcopy(data)
        png_path = DRAWABLE / (fallback + '.png')
        webp_path = DRAWABLE / (fallback + '.webp')
        if png_path.exists():
            webp_path.write_bytes(encode_lossless_webp(png_path.read_bytes()))
        image = Image.open(webp_path).convert('RGBA')
        pixel_manifest[fallback] = {'size': list(image.size), 'rgba_sha256': hashlib.sha256(image.tobytes()).hexdigest()}
        images = [a for a in data['assets'] if 'p' in a]
        assert len(images) == 1 and images[0]['id'] == 'maid_bitmap'
        asset = images[0]
        if asset['p'].startswith('data:'):
            embedded = Image.open(io.BytesIO(base64.b64decode(asset['p'].split(',', 1)[1], validate=True))).convert('RGBA')
            assert embedded.size == image.size and embedded.tobytes() == image.tobytes()
        else:
            assert asset['p'] in (fallback + '.png', fallback + '.webp')
        asset.update(p=fallback + '.webp', u='', e=0)
        # Only image packaging may change; preserve all gestures, masks and timings.
        original['assets'] = data['assets']
        assert original == data
        path.write_text(json.dumps(data, separators=(',', ':')) + '\n')
        after += path.stat().st_size
        png_path.unlink(missing_ok=True)
    manifest_path = ART / 'static-image-pixels.json'
    if manifest_path.exists():
        assert json.loads(manifest_path.read_text()) == pixel_manifest, 'Packaged artwork pixels changed'
    else:
        manifest_path.write_text(json.dumps(pixel_manifest, indent=2) + '\n')
    build_preview()
    print(f'JSON bytes: {before} -> {after}; all 14 lossless WebPs are shared with static fallbacks.')

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--preview-only', action='store_true',
                        help='Generate preview.html from the current app resources without rewriting them.')
    if parser.parse_args().preview_only:
        build_preview()
    else:
        package_existing()
