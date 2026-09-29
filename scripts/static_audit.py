#!/usr/bin/env python3
"""Repository invariants only. This is NOT a Kotlin compiler or runtime test suite."""
from pathlib import Path
import hashlib
import json
import re
import sys
import xml.etree.ElementTree as ET
import zipfile

root = Path(__file__).resolve().parents[1]
passed = []
def check(label, condition):
    if not condition:
        print('FAIL:', label)
        sys.exit(1)
    passed.append(label)
    print('PASS:', label)

check('no GitHub Actions workflows', not list(root.glob('.github/workflows/*')))
sources = list(root.rglob('*.kt'))
production = [p for p in sources if '/main/' in str(p)]
text = '\n'.join(p.read_text() for p in production)
check('no WebView implementation', not re.search(r'\b(?:android\.webkit|WebView)\b', text))
check('no legacy notebook write key in production', 'nishany.notebook.notes' not in text)
check('current notebook endpoint present', '/api/notebook/notes' in text)
check('university projection endpoint present', '/api/me/university' in text)
check('HTTP automatic retries disabled', '.retryOnConnectionFailure(false)' in text)
check('HTTP redirects disabled', '.followRedirects(false)' in text and '.followSslRedirects(false)' in text)
check('all mutation methods receive Origin', 'if (writing) builder.header("Origin", originHeader)' in text)
check('write journal persisted before PUT in source', 'DraftPhase.SENDING' in text)
check('encrypted draft storage uses Android Keystore and AtomicFile', all(s in text for s in ['AndroidKeyStore', 'AES/GCM/NoPadding', 'AtomicFile', 'noBackupFilesDir']))
check('no reference repository content committed', not (root / 'references').exists())
for path in root.rglob('*.xml'):
    ET.parse(path)
check('XML files parse', True)
manifest = ET.parse(root / 'app/src/main/AndroidManifest.xml').getroot()
ns = '{http://schemas.android.com/apk/res/android}'
app = manifest.find('application')
check('backup disabled', app.get(ns+'allowBackup') == 'false')
check('cleartext disabled', app.get(ns+'usesCleartextTraffic') == 'false')
check('RTL enabled', app.get(ns+'supportsRtl') == 'true')
jar = root / 'gradle/wrapper/gradle-wrapper.jar'
check('official wrapper JAR digest', hashlib.sha256(jar.read_bytes()).hexdigest() == '81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f')
with zipfile.ZipFile(jar) as archive:
    check('wrapper entry point present', 'org/gradle/wrapper/GradleWrapperMain.class' in archive.namelist())
check('no private keys/signing files', not any(p.suffix in {'.pem', '.jks', '.keystore'} for p in root.rglob('*') if p.is_file()))
check('no apparent access tokens', not re.search(r'gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|-----BEGIN .*PRIVATE KEY', text))
unit = sum(p.read_text().count('@Test') for p in root.glob('core/src/test/**/*.kt'))
device = sum(p.read_text().count('@Test') for p in root.glob('app/src/androidTest/**/*.kt'))
print(json.dumps({'static_checks_passed': len(passed), 'jvm_tests_authored': unit, 'device_tests_authored': device,
                  'jvm_tests_executed': 0, 'device_tests_executed': 0, 'compilation_verified': False}))
