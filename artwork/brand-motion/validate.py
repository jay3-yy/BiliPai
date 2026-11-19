"""Static packaging checks; does not invoke Gradle or compile the application."""
import base64
import io
import hashlib
import json
import re
from pathlib import Path
from PIL import Image
from package_assets import render_preview

def check_keyframes(value, end):
    if isinstance(value, dict):
        if value.get('a') == 1 and isinstance(value.get('k'), list):
            frames = value['k']
            times = [frame['t'] for frame in frames]
            assert times == sorted(set(times)), 'Animation times must increase strictly'
            assert 0 <= times[0] <= times[-1] <= end
            assert all('s' in frame for frame in frames), 'Every key must define a finite pose'
        for child in value.values(): check_keyframes(child, end)
    elif isinstance(value, list):
        for child in value: check_keyframes(child, end)

pose_bitmaps = set()
ROOT = Path(__file__).resolve().parents[2]
for name,frames in [('welcome',60),('clean_complete',72),('cleaning',96),('retry',90),('empty',108),('search_empty',120),('favorite_saved',72),('follow_success',72),('unfollow_complete',60),('dislike_confirmed',60),('share_ready',72),('coin_success',72),('download_complete',72),('triple_success',108)]:
    path=ROOT/'brand-motion/src/main/res/raw'/f'bilipai_maid_{name}.json'
    data=json.loads(path.read_text())
    assert data['op']==frames and data['fr']==60
    assert data['w']==512 and data['h']==512
    check_keyframes(data['layers'], frames)
    assets={a['id']:a for a in data['assets']}
    names={layer['nm'] for layer in data['layers']}
    if name not in ('empty','triple_success','unfollow_complete'):
        assert any('eye' in layer_name.lower() for layer_name in names)
        assert any('closed eyelash' in layer_name for layer_name in names)
    required={'welcome':'Greeting hand','clean_complete':'Held broom sweep','cleaning':'Held broom working sweep',
              'retry':'Disconnected connectors','empty':'Held empty collection box','search_empty':'Held looking glass','favorite_saved':'Held favorite star',
              'dislike_confirmed':'Crossed arms restrained head shake','share_ready':'Open palm invitation','coin_success':'Held white coin and pinching fingers',
              'follow_success':'Grateful chest hand','unfollow_complete':'Quiet joined hands','download_complete':'Thumbs up hand','triple_success':'Double fist happy hop'}
    assert required[name] in names
    if name=='cleaning':
        assert not any('badge' in n.lower() or 'completion' in n.lower() for n in names)
        for item in data['layers']:
            for value in item['ks'].values():
                if value.get('a') == 1:
                    assert value['k'][0]['s'] == value['k'][-1]['s'], 'Working cycle must close seamlessly'
    if name in ('follow_success','unfollow_complete'):
        assert not any('badge' in n.lower() or 'star' in n.lower() or 'card' in n.lower() for n in names)
        if name=='unfollow_complete':
            assert not any('eye' in n.lower() for n in names), 'Closed eyes must not be given an artificial blink'
    if name=='coin_success':
        assert 'Left action eye' in names and 'Right action eye' not in names, 'Preserve the already closed winking eye'
    if name=='triple_success':
        assert {'Like success badge','Coin success badge','Favorite success badge'} <= names
        assert len([n for n in names if n.startswith('Celebration fist')])==2
    if name!='welcome': assert 'Greeting hand' not in names
    bitmaps=0
    for asset in assets.values():
        if 'p' in asset:
            assert asset['u']=='' and asset['e']==0 and asset['id']=='maid_bitmap'
            fallback_name = {'welcome':'bilipai_maid_static', 'clean_complete':'bilipai_maid_clean_static','cleaning':'bilipai_maid_cleaning_static',
                             'retry':'bilipai_maid_retry_static', 'empty':'bilipai_maid_empty_static',
                             'search_empty':'bilipai_maid_search_empty_static',
                             'favorite_saved':'bilipai_maid_favorite_static','download_complete':'bilipai_maid_download_static',
                             'dislike_confirmed':'bilipai_maid_dislike_static','share_ready':'bilipai_maid_share_static','coin_success':'bilipai_maid_coin_static',
                             'follow_success':'bilipai_maid_follow_static','unfollow_complete':'bilipai_maid_unfollow_static','triple_success':'bilipai_maid_triple_static'}[name]
            assert asset['p'] == fallback_name+'.webp', 'Animation must reference its matching static fallback'
            decoded = (ROOT/'brand-motion/src/main/res/drawable-nodpi'/asset['p']).read_bytes()
            image=Image.open(io.BytesIO(decoded)); image.load()
            assert image.size==(asset['w'],asset['h']) and image.mode=='RGBA'
            assert image.getpixel((0,0))[3]==0
            if name in ('retry','empty','search_empty'):
                assert decoded not in pose_bitmaps, 'State poses must have distinct artwork'
                pose_bitmaps.add(decoded)
            bitmaps+=1
    assert bitmaps==1, 'Each timeline must reference one shared image, not embed it or duplicate it per layer'
    for layers in [data['layers']]+[a['layers'] for a in assets.values() if 'layers' in a]:
        indexes={l['ind'] for l in layers}
        assert len(indexes)==len(layers)
        for layer in layers:
            assert layer['ip']<layer['op']
            if 'refId' in layer: assert layer['refId'] in assets
            if 'parent' in layer: assert layer['parent'] in indexes and layer['parent']!=layer['ind']
            for mask in layer.get('masksProperties',[]):
                shape=mask['pt']['k']
                assert len(shape['v'])==len(shape['i'])==len(shape['o']) and shape['c']
                assert mask['mode'] in ('a','s')
    print(f'{name}: {frames/60:g}s, {len(data["layers"])} layers, local WebP and references valid')
for name in ['bilipai_maid_static','bilipai_maid_clean_static','bilipai_maid_retry_static','bilipai_maid_empty_static','bilipai_maid_search_empty_static','bilipai_maid_favorite_static','bilipai_maid_download_static','bilipai_maid_triple_static']:
    assert (ROOT/'brand-motion/src/main/res/drawable-nodpi'/f'{name}.webp').exists()
preview=render_preview()
assert '__ANIMATIONS__' not in preview
assert 'https://' not in preview
assert (Path(__file__).parent/'lottie.min.js').is_file()
assert (Path(__file__).parent/'LOTTIE-WEB-LICENSE.md').is_file()
player=(ROOT/'brand-motion/src/main/java/com/android/purebilibili/core/ui/BlueSnowMaidAnimation.kt').read_text()
for state,resource,duration,fallback in re.findall(r'(\w+)\(R.raw.(\w+), (\d+)L, R.drawable.(\w+)',player):
    timeline=json.loads((ROOT/'brand-motion/src/main/res/raw'/f'{resource}.json').read_text())
    assert (timeline['op']-timeline['ip'])/timeline['fr']*1000==int(duration), f'{state}: resource/player duration mismatch'
    assert next(a['p'] for a in timeline['assets'] if 'p' in a)==fallback+'.webp', f'{state}: resource/player shared image mismatch'
print('All enum playback durations match their resources.')
print('Static fallbacks and offline interactive preview valid.')

pixels = json.loads((Path(__file__).parent / 'static-image-pixels.json').read_text())
for stem, expected in pixels.items():
    image = Image.open(ROOT / 'brand-motion/src/main/res/drawable-nodpi' / (stem + '.webp')).convert('RGBA')
    assert list(image.size) == expected['size']
    assert hashlib.sha256(image.tobytes()).hexdigest() == expected['rgba_sha256'], stem
print('All 14 decoded RGBA images match the original PNG pixel hashes.')
