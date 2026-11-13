#!/usr/bin/env python3
"""Build and update the separate V2 preview app on one ADB device."""

import argparse
import hashlib
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APK = ROOT / 'app/build/outputs/apk/preview/app-preview.apk'
PACKAGE = 'fr.loevan.jeancalcul.v2.preview'


def devices():
    output = subprocess.check_output(['adb', 'devices'], text=True)
    return [line.split()[0] for line in output.splitlines()[1:]
            if len(line.split()) >= 2 and line.split()[1] == 'device']


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', help='ADB serial when several devices are connected')
    args = parser.parse_args()
    available = devices()
    if args.serial and args.serial not in available:
        parser.error(f'{args.serial} is not an authorized ADB device: {available}')
    if not args.serial and len(available) != 1:
        parser.error(f'connect exactly one authorized ADB device or use --serial: {available}')
    serial = args.serial or available[0]

    subprocess.run(['./gradlew', ':app:assemblePreview'], cwd=ROOT, check=True)
    if not APK.is_file():
        raise FileNotFoundError(APK)
    digest = hashlib.sha256(APK.read_bytes()).hexdigest()
    print(f'APK: {APK}\nSHA-256: {digest}\nDevice: {serial}', flush=True)

    install = subprocess.run(['adb', '-s', serial, 'install', '-r', '-t', str(APK)],
                             text=True, capture_output=True)
    if install.returncode:
        print(install.stdout + install.stderr, file=sys.stderr)
        print('Installation failed. If Android reports UPDATE_INCOMPATIBLE, this '
              'device has a Preview APK signed with another key. Its data was '
              'kept; use an APK signed with the same key or decide separately '
              'whether Preview data may be erased.', file=sys.stderr)
        return install.returncode
    print(install.stdout.strip(), flush=True)
    subprocess.run(['adb', '-s', serial, 'shell', 'am', 'start', '-n',
                    f'{PACKAGE}/fr.loevan.jeancalcul.v2.MainActivity'], check=True)
    return 0


if __name__ == '__main__':
    sys.exit(main())
