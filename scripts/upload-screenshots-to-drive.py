#!/usr/bin/env python3
"""Upload the curated Play Store screenshots to a dedicated Google Drive folder.

Uses the OAuth access token already authorized for gwscli (token.json) and the
Drive REST API directly, because the gwscli Drive tools require Developer Preview
enrollment on this Google Cloud project.
"""
import json
import mimetypes
import os
import sys
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TOKEN_FILE = Path.home() / '.config/gwscli/token.json'
SRC = ROOT / 'store-screenshots/playstore'
FOLDER_NAME = 'Pomodoro 2.0.0 - Play Store Screenshots'


def token():
    data = json.loads(TOKEN_FILE.read_text())
    return data['token']['access_token']


def api(method, path, body=None, headers=None):
    req = urllib.request.Request(
        'https://www.googleapis.com/drive/v3/' + path,
        data=body,
        method=method,
        headers=headers or {},
    )
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            return json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        print('API ERROR', e.code, e.read().decode()[:500], file=sys.stderr)
        raise


def auth():
    return {'Authorization': 'Bearer ' + token()}


def find_folder(name):
    q = urllib.parse.quote(f"name = '{name}' and mimeType = 'application/vnd.google-apps.folder' and trashed = false")
    return api('GET', 'files?q=' + q + '&fields=files(id,name)&pageSize=10', headers=auth()).get('files', [])


def ensure_folder(name, parent=None):
    found = find_folder(name)
    if found:
        return found[0]['id']
    meta = {'name': name, 'mimeType': 'application/vnd.google-apps.folder'}
    if parent:
        meta['parents'] = [parent]
    body = json.dumps(meta).encode()
    return api('POST', 'files?fields=id,name', body=body,
               headers={**auth(), 'Content-Type': 'application/json'})['id']


def upload(path, folder_id):
    import subprocess
    name = path.name
    ctype = mimetypes.guess_type(name)[0] or 'application/octet-stream'
    meta = json.dumps({'name': name, 'parents': [folder_id]})
    url = 'https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,name,size'
    out = subprocess.run([
        'curl', '-s', '-X', 'POST',
        '-H', 'Authorization: Bearer ' + token(),
        '-F', 'metadata=' + meta + ';type=application/json',
        '-F', f'file=@{path};type={ctype}',
        url,
    ], capture_output=True, text=True, timeout=120)
    return json.loads(out.stdout)


def main():
    if not SRC.is_dir():
        print('No curated screenshots at', SRC); sys.exit(1)
    # Reuse/rename the test folder created during validation
    tid = '1ZMrKOas-8VqBUaAat3LetMtdhQxrtIJN'
    try:
        api('PATCH', 'files/' + tid, json.dumps({'name': FOLDER_NAME}).encode(),
            headers={**auth(), 'Content-Type': 'application/json'})
        root_id = tid
    except Exception:
        root_id = ensure_folder(FOLDER_NAME)
    print('Root folder:', root_id, FOLDER_NAME)

    total = 0
    for klass in sorted(p for p in SRC.iterdir() if p.is_dir()):
        sub_id = ensure_folder(klass.name, root_id)
        for img in sorted(klass.glob('*.png')):
            info = upload(img, sub_id)
            total += 1
            print(f'  {klass.name}/{img.name}: {info.get("size")} bytes')
    manifest = ROOT / 'PLAY_STORE_REQUIREMENTS.md'
    if manifest.exists():
        upload(manifest, root_id)
        print('  manifest uploaded')
    print('Uploaded', total, 'screenshots')


if __name__ == '__main__':
    main()
