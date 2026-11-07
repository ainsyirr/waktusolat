"""Publish Waktu Solat to https://ainsyirr.github.io/waktusolat/

Usage:  python publish.py
Uses the GitHub token at C:/Users/user/.orthopass/gh_token.txt (scope: public_repo).
First run creates the repo + enables GitHub Pages; later runs just push changes.
After changing files, bump CACHE in sw.js (waktusolat-v1 -> v2) so phones pick up the update.
"""
import json, os, subprocess, time, urllib.request, urllib.error
HERE = os.path.dirname(os.path.abspath(__file__))
TOK = open(os.path.expanduser('~/.orthopass/gh_token.txt')).read().strip()
OWNER, REPO = 'ainsyirr', 'waktusolat'
URL = f'https://{OWNER}.github.io/{REPO}/'

def api(method, path, body=None):
    req = urllib.request.Request('https://api.github.com' + path, method=method,
        data=json.dumps(body).encode() if body is not None else None,
        headers={'Authorization': 'Bearer ' + TOK, 'Accept': 'application/vnd.github+json', 'User-Agent': 'waktusolat-publish'})
    try:
        with urllib.request.urlopen(req, timeout=60) as r: return r.status, json.loads(r.read() or b'{}')
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read() or b'{}')

env = dict(os.environ, GIT_TERMINAL_PROMPT='0', GCM_INTERACTIVE='never')
def git(*a):
    r = subprocess.run(['git', '-C', HERE, '-c', 'credential.helper='] + list(a), capture_output=True, text=True, env=env, timeout=600)
    print('git', a[0], r.returncode, (r.stdout + r.stderr).replace(TOK, '***').strip()[-400:])
    return r.returncode

st, _ = api('GET', f'/repos/{OWNER}/{REPO}')
if st == 404:
    st, j = api('POST', '/user/repos', {'name': REPO, 'description': 'Waktu solat JAKIM + azan automatik (PWA)', 'homepage': URL, 'auto_init': False})
    print('create repo', st, j.get('html_url') or j.get('message'))

if not os.path.isdir(os.path.join(HERE, '.git')):
    git('init', '-b', 'main')
    git('config', 'user.name', OWNER); git('config', 'user.email', f'{OWNER}@users.noreply.github.com')
open(os.path.join(HERE, '.gitignore'), 'w').write('__pycache__/\nandroid/build/\n')
git('add', '-A')
git('commit', '-m', 'Update Waktu Solat ' + time.strftime('%Y-%m-%d %H:%M'))
rc = git('push', f'https://x-access-token:{TOK}@github.com/{OWNER}/{REPO}.git', 'HEAD:main')

st, j = api('GET', f'/repos/{OWNER}/{REPO}/pages')
if st == 404:
    st, j = api('POST', f'/repos/{OWNER}/{REPO}/pages', {'source': {'branch': 'main', 'path': '/'}})
    print('enable pages', st, j.get('html_url') or j.get('message'))
print(('PUBLISHED - live in ~1-2 minutes at ' + URL) if rc == 0 else 'PUSH FAILED')
