"""Build WaktuSolat.apk without Gradle: aapt2 -> javac -> d8 -> zipalign -> apksigner.

Usage:  python build.py            (version from version.txt: "<code> <name>")
Toolchain: C:/Users/user/Android/{jdk,sdk}. Signing key: C:/Users/user/Android/keys/ (keep it! updates
must be signed with the same key or Android refuses to install over the old app).
"""
import glob, os, secrets, shutil, subprocess, sys, zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
WEB = os.path.dirname(HERE)
TOOLS = r"C:\Users\user\Android"
JDK = os.path.join(TOOLS, "jdk")
SDK = os.path.join(TOOLS, "sdk")
BT = os.path.join(SDK, "build-tools", "34.0.0")
JAR = os.path.join(SDK, "platforms", "android-34", "android.jar")
KEYS = os.path.join(TOOLS, "keys")
KS, PASSF, ALIAS = os.path.join(KEYS, "waktusolat.keystore"), os.path.join(KEYS, "waktusolat.pass"), "waktusolat"
OUT = os.path.join(HERE, "build")
ENV = dict(os.environ, JAVA_HOME=JDK, PATH=os.path.join(JDK, "bin") + os.pathsep + os.environ["PATH"])

def run(*cmd):
    r = subprocess.run([str(c) for c in cmd], capture_output=True, text=True, env=ENV)
    out = (r.stdout + r.stderr).strip()
    if r.returncode:
        print("FAILED:", " ".join(map(str, cmd))[:300]); print(out[-4000:]); sys.exit(1)
    return out

code, name = open(os.path.join(HERE, "version.txt")).read().split()
shutil.rmtree(OUT, ignore_errors=True)
for d in ("assets/www/audio", "gen", "classes", "dex"):
    os.makedirs(os.path.join(OUT, d), exist_ok=True)

# 1. web UI + audio as assets
for f in ("index.html", "manifest.webmanifest", "icon.svg", "icon-192.png", "icon-512.png"):
    shutil.copy(os.path.join(WEB, f), os.path.join(OUT, "assets", "www", f))
for f in glob.glob(os.path.join(WEB, "audio", "*.mp3")):
    shutil.copy(f, os.path.join(OUT, "assets", "www", "audio"))

# 2. resources
aapt2 = os.path.join(BT, "aapt2.exe")
run(aapt2, "compile", "--dir", os.path.join(HERE, "res"), "-o", os.path.join(OUT, "res.zip"))
run(aapt2, "link", "-I", JAR, "--manifest", os.path.join(HERE, "AndroidManifest.xml"),
    "--min-sdk-version", "26", "--target-sdk-version", "34", "--version-code", code, "--version-name", name,
    "-A", os.path.join(OUT, "assets"), "-0", "mp3", "--java", os.path.join(OUT, "gen"),
    "-o", os.path.join(OUT, "base.apk"), os.path.join(OUT, "res.zip"))

# 3. java -> class -> dex
srcs = glob.glob(os.path.join(HERE, "src", "**", "*.java"), recursive=True) + glob.glob(os.path.join(OUT, "gen", "**", "*.java"), recursive=True)
print(run(os.path.join(JDK, "bin", "javac.exe"), "-source", "8", "-target", "8", "-Xlint:-options", "-encoding", "UTF-8",
          "-bootclasspath", JAR + os.pathsep + os.path.join(BT, "core-lambda-stubs.jar"), "-classpath", JAR, "-d", os.path.join(OUT, "classes"), *srcs) or "javac ok")
classes = glob.glob(os.path.join(OUT, "classes", "**", "*.class"), recursive=True)
run(os.path.join(BT, "d8.bat"), "--release", "--min-api", "26", "--lib", JAR, "--output", os.path.join(OUT, "dex"), *classes)

# 4. package, align, sign
unsigned = os.path.join(OUT, "unsigned.apk")
shutil.copy(os.path.join(OUT, "base.apk"), unsigned)
with zipfile.ZipFile(unsigned, "a", zipfile.ZIP_DEFLATED) as z:
    z.write(os.path.join(OUT, "dex", "classes.dex"), "classes.dex")
aligned = os.path.join(OUT, "aligned.apk")
run(os.path.join(BT, "zipalign.exe"), "-f", "-p", "4", unsigned, aligned)

if not os.path.exists(KS):
    os.makedirs(KEYS, exist_ok=True)
    open(PASSF, "w").write(secrets.token_urlsafe(24))
    run(os.path.join(JDK, "bin", "keytool.exe"), "-genkeypair", "-keystore", KS, "-storetype", "PKCS12", "-alias", ALIAS,
        "-keyalg", "RSA", "-keysize", "3072", "-validity", "12000", "-storepass:file", PASSF,
        "-dname", "CN=Waktu Solat, O=ainsyirr, C=MY")
    print("created signing key", KS)
apk = os.path.join(OUT, "WaktuSolat.apk")
run(os.path.join(BT, "apksigner.bat"), "sign", "--ks", KS, "--ks-key-alias", ALIAS, "--ks-pass", "file:" + PASSF,
    "--out", apk, aligned)
print(run(os.path.join(BT, "apksigner.bat"), "verify", "--print-certs", apk).splitlines()[0])
print("BUILT", apk, os.path.getsize(apk), "bytes, version", name, "(" + code + ")")
