#!/usr/bin/env python3
"""Refresh checked-in icon subsets from pinned Gradle source archives; runs no Gradle tasks."""
import argparse
import hashlib
import json
from pathlib import Path
import re
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]
PACKAGES = {
    'Icons': 'androidx.compose.material.icons',
}
ARCHIVES = (
    ('material-icons', 'androidx.compose.material', 'material-icons-extended-android', '1.7.8'),
)


def references(source):
    source = re.sub(r'//[^\n]*|/\*.*?\*/|""".*?"""|"(?:\\.|[^"\\])*"', ' ', source, flags=re.S)
    result = set()
    for package in PACKAGES.values():
        result.update(re.findall(r'\b' + re.escape(package) + r'\.[\w.]+', source))
    # Resolve receiver access as well as imports, including files using wildcard imports.
    for receiver, package in PACKAGES.items():
        for match in re.finditer(r'\b' + receiver + r'((?:\s*\.\s*\w+){2,3})', source):
            parts = re.findall(r'\w+', match.group(1))
            parts = parts[:3] if parts[0] == 'AutoMirrored' else parts[:2]
            style = parts[:-1]
            style = [('Filled' if receiver == 'Icons' else 'Outlined') if x == 'Default' else x for x in style]
            result.add(package + '.' + '.'.join(x.lower() for x in style) + '.' + parts[-1])
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--gradle-cache', type=Path, default=Path.home() / '.gradle/caches/modules-2/files-2.1')
    args = parser.parse_args()
    index = {}
    metadata = {}
    for module, group, artifact, version in ARCHIVES:
        matches = list((args.gradle_cache / group / artifact / version).glob('*/*-sources.jar'))
        if len(matches) != 1:
            raise RuntimeError(f'Expected one source archive for {group}:{artifact}:{version}, found {len(matches)}')
        archive = matches[0]
        metadata[module] = {'coordinate': f'{group}:{artifact}:{version}', 'archiveSha256': hashlib.sha256(archive.read_bytes()).hexdigest()}
        with ZipFile(archive) as zip_file:
            for name in zip_file.namelist():
                if not name.endswith('.kt'):
                    continue
                source = zip_file.read(name).decode()
                package = re.search(r'^package (\S+)', source, re.M).group(1)
                key = package + '.' + Path(name).stem
                index[key] = (module, name[len('commonMain/'):] if name.startswith('commonMain/') else name, source)
    requested = set()
    for module in ROOT.iterdir():
        if module.name in metadata or not (module / 'src').is_dir():
            continue
        for source_file in (module / 'src').rglob('*'):
            if source_file.suffix in ('.kt', '.java'):
                requested.update(references(source_file.read_text()))
    # Match full imports and receiver-derived paths; retain dependencies of deprecated aliases.
    selected = set()
    while requested:
        reference = requested.pop()
        keys = [reference] if reference in index else []
        for key in keys:
            if key not in selected:
                selected.add(key)
                requested.update(references(index[key][2]))
    for module in metadata:
        target = ROOT / module / 'src/main/kotlin'
        for stale in target.rglob('*.kt'):
            stale.unlink()
        entries = {}
        for key in sorted(selected):
            owner, relative, source = index[key]
            if owner != module:
                continue
            output = target / relative
            output.parent.mkdir(parents=True, exist_ok=True)
            output.write_text(source)
            entries[relative] = hashlib.sha256(source.encode()).hexdigest()
        metadata[module]['files'] = entries
        print(f'{module}: {len(entries)} / {sum(owner == module for owner, _, _ in index.values())} icon files')
    (ROOT / 'artwork/curated-icons/sources.json').write_text(json.dumps(metadata, indent=2) + '\n')


if __name__ == '__main__':
    main()
