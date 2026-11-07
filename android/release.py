"""Upload android/build/WaktuSolat.apk as a GitHub release of ainsyirr/waktusolat.

Usage:  python release.py      (run build.py first; bump version.txt for every new release)
Stable download link: https://github.com/ainsyirr/waktusolat/releases/latest/download/WaktuSolat.apk
"""
import json, os, urllib.request, urllib.error
HERE = os.path.dirname(os.path.abspath(__file__))
TOK = open(os.path.expanduser('~/.orthopass/gh_token.txt')).read().strip()
REPO = 'ainsyirr/waktusolat'
APK = os.path.join(HERE, 'build', 'WaktuSolat.apk')
code, name = open(os.path.join(HERE, 'version.txt')).read().split()
TAG = 'v' + name

def api(method, url, body=None, data=None, ctype='application/json'):
    req = urllib.request.Request(url if url.startswith('http') else 'https://api.github.com' + url, method=method,
        data=data if data is not None else (json.dumps(body).encode() if body is not None else None),
        headers={'Authorization': 'Bearer ' + TOK, 'Accept': 'application/vnd.github+json',
                 'User-Agent': 'waktusolat-release', 'Content-Type': ctype})
    try:
        with urllib.request.urlopen(req, timeout=600) as r: return r.status, json.loads(r.read() or b'{}')
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read() or b'{}')

st, rel = api('GET', f'/repos/{REPO}/releases/tags/{TAG}')
if st == 404:
    notes = ("Waktu Solat untuk Android: azan berbunyi walaupun app ditutup.\n\n"
             "Pasang: muat turun WaktuSolat.apk di bawah, buka fail, benarkan \"Pasang apl tidak diketahui\".")
    st, rel = api('POST', f'/repos/{REPO}/releases', {'tag_name': TAG, 'target_commitish': 'main',
                  'name': f'Waktu Solat {name}', 'body': notes, 'make_latest': 'true'})
    print('create release', st, rel.get('html_url') or rel)
for a in rel.get('assets', []):
    if a['name'] == 'WaktuSolat.apk':
        print('delete old asset', api('DELETE', f"/repos/{REPO}/releases/assets/{a['id']}")[0])
upload = rel['upload_url'].split('{')[0] + '?name=WaktuSolat.apk'
st, a = api('POST', upload, data=open(APK, 'rb').read(), ctype='application/vnd.android.package-archive')
print('upload', st, a.get('browser_download_url') or a)
