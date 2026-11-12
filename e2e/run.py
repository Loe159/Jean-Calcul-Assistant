#!/usr/bin/env python3
"""Own an isolated AVD, run technical checks, collect evidence. Never judge UX."""
import argparse
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import socket
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
APP = 'fr.loevan.jeancalcul.v2'
RUNNER = APP + '.test/androidx.test.runner.AndroidJUnitRunner'
SCENARIOS = ('empty', 'message', 'reset', 'settings', 'dark', 'small_screen',
             'large_text', 'keyboard', 'reduced_motion', 'regression_fixture')


def redact(text):
    text = re.sub(r'(?i)(bearer\s+|(?:api[_-]?key|token|password|secret)\s*[=:]\s*)[^\s,;]+', r'\1[REDACTED]', text)
    text = re.sub(r'\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b', '[EMAIL]', text)
    return text.replace(str(Path.home()), '[HOME]')


def check_instrumentation_results(directory, serial, since=0):
    """Don't trust Gradle's exit code alone (UTP can leave failures in XML)."""
    matched = []
    for path in directory.rglob('TEST-*.xml'):
        if path.stat().st_mtime < since:
            continue
        root = ET.parse(path).getroot()
        if not any(prop.get('name') == 'device' and prop.get('value') == serial
                   for prop in root.iter('property')):
            continue
        matched.append(path)
        if list(root.iter('failure')) or list(root.iter('error')):
            raise RuntimeError(f'Instrumentation XML contains a failure: {path.name}')
        if not list(root.iter('testcase')):
            raise RuntimeError(f'Instrumentation XML contains no tests: {path.name}')
    if not matched:
        raise RuntimeError(f'No fresh instrumentation XML for {serial}')
    return matched


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--run-id', default=dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%SZ'))
    parser.add_argument('--attempt', type=int, choices=(1, 2, 3), default=1)
    parser.add_argument('--show-window', action='store_true')
    parser.add_argument('--keep-emulator', action='store_true', help='Leave the synthetic AVD open for interaction')
    parser.add_argument('--port', type=int, default=5580)
    args = parser.parse_args()
    if not re.fullmatch(r'[A-Za-z0-9_-]+', args.run_id):
        parser.error('run-id must contain only letters, numbers, underscore or dash')
    if args.port < 5554 or args.port > 5682 or args.port % 2:
        parser.error('port must be even, between 5554 and 5682')
    out = ROOT / 'e2e-artifacts' / args.run_id / f'attempt-{args.attempt}'
    out.mkdir(parents=True, exist_ok=False)  # Never overwrite evidence or silently retry.
    (out / 'screenshots').mkdir()
    (out / 'logs').mkdir()
    report = {'issue': 54, 'attempt': args.attempt, 'created_utc': dt.datetime.now(dt.timezone.utc).isoformat(),
              'commit': '', 'working_tree_dirty': False, 'api': 35, 'serial': f'emulator-{args.port}',
              'technical': {'status': 'NOT_RUN'},
              'captures': {s: {'status': 'NOT_RUN'} for s in SCENARIOS},
              'visual_review': {'status': 'NOT_RUN', 'reason': 'Images must be opened by the implementing agent'},
              'reference_comparison': {'status': 'NOT_RUN', 'reason': 'Pinned Eta references must be opened and compared by the agent'},
              'regression_detection': {'status': 'NOT_RUN'}}
    def save():
        (out / 'report.json').write_text(json.dumps(report, indent=2, ensure_ascii=False) + '\n')
    def run(cmd, name=None, timeout=180, check=True, input=None):
        def log_output(stdout, stderr):
            if not name:
                return
            if isinstance(stdout, bytes):
                stdout = stdout.decode(errors='replace')
            if isinstance(stderr, bytes):
                stderr = stderr.decode(errors='replace')
            (out / 'logs' / f'{name}.txt').write_text(redact((stdout or '') + (stderr or '')))
        command = [str(c) for c in cmd]
        with subprocess.Popen(command, cwd=ROOT, env=env, stdin=subprocess.PIPE,
                              stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True) as process:
            deadline = time.monotonic() + timeout
            pending_input = input
            while True:
                try:
                    stdout, stderr = process.communicate(input=pending_input,
                        timeout=max(0.01, min(10, deadline - time.monotonic())))
                    break
                except subprocess.TimeoutExpired as error:
                    pending_input = None
                    log_output(error.stdout, error.stderr)
                    if time.monotonic() >= deadline:
                        process.kill()
                        stdout, stderr = process.communicate()
                        log_output(stdout, stderr)
                        raise subprocess.TimeoutExpired(command, timeout, stdout, stderr)
            result = subprocess.CompletedProcess(command, process.returncode, stdout, stderr)
        log_output(result.stdout, result.stderr)
        if check and result.returncode:
            raise RuntimeError(f'{cmd[0]} failed ({result.returncode}); see logs/{name}.txt')
        return result
    env = os.environ.copy()
    emulator = None
    log = None
    adb = None
    success = False
    save()
    try:
        report['commit'] = run(['git', 'rev-parse', 'HEAD']).stdout.strip()
        report['working_tree_dirty'] = bool(run(['git', 'status', '--porcelain']).stdout.strip())
        try:
            from references import fetch
            fetch(out / 'references')
        except Exception as error:
            report['reference_comparison']['reason'] = 'Reference download unavailable: ' + redact(str(error))
        paths = run(['git', 'ls-files', '-c', '-o', '--exclude-standard', '-z']).stdout.split('\0')
        source_hashes = {p: hashlib.sha256((ROOT / p).read_bytes()).hexdigest()
                         for p in paths if p and (ROOT / p).is_file()}
        (out / 'source-manifest.json').write_text(json.dumps(source_hashes, indent=2) + '\n')
        report['source_manifest'] = 'source-manifest.json'
        # All tools are explicit; no system installation or license acceptance on the user's behalf.
        sdk = Path(env.get('ANDROID_HOME', env.get('ANDROID_SDK_ROOT', '')))
        def tool(name, candidates):
            found = shutil.which(name)
            if found:
                return found
            for candidate in candidates:
                if candidate.is_file():
                    return str(candidate)
            raise RuntimeError(f'Missing {name}. See e2e/README.md prerequisites.')
        adb_bin = tool('adb', [sdk / 'platform-tools/adb'])
        emulator_bin = tool('emulator', [sdk / 'emulator/emulator'])
        avdmanager = tool('avdmanager', sorted(sdk.glob('cmdline-tools/*/bin/avdmanager'), reverse=True))
        tool('java', [])
        for port in (args.port, args.port + 1):
            with socket.socket() as probe:
                if probe.connect_ex(('127.0.0.1', port)) == 0:
                    raise RuntimeError(f'Port {port} occupied; choose --port. Existing devices are never reused.')
        avd_home = out / 'avd'
        avd_home.mkdir()
        env['ANDROID_AVD_HOME'] = str(avd_home)
        env['ANDROID_USER_HOME'] = str(out / 'android-user')
        env['ANDROID_EMULATOR_HOME'] = str(out / 'android-user')
        (out / 'android-user').mkdir()
        env['ANDROID_SERIAL'] = report['serial']
        name = f'jc-e2e-{args.run_id}-{args.attempt}'
        image = 'system-images;android-35;google_apis;x86_64'
        run([avdmanager, 'create', 'avd', '-n', name, '-k', image, '--device', 'pixel_6'],
            'create-avd', input='no\n')
        log = (out / 'logs/emulator-local.txt').open('w')
        cmd = [emulator_bin, '-avd', name, '-port', str(args.port), '-wipe-data', '-no-snapshot',
               '-no-audio', '-no-boot-anim', '-gpu', 'swiftshader', '-memory', '2048', '-cores', '2']
        if not args.show_window:
            cmd.append('-no-window')
        emulator = subprocess.Popen(cmd, env=env, stdout=log, stderr=subprocess.STDOUT)
        adb = [adb_bin, '-s', report['serial']]
        deadline = time.monotonic() + 240
        while time.monotonic() < deadline:
            if emulator.poll() is not None:
                raise RuntimeError('Emulator exited; see logs/emulator-local.txt')
            boot = run(adb + ['shell', 'getprop', 'sys.boot_completed'], check=False, timeout=15)
            if boot.stdout.strip() == '1':
                break
            time.sleep(2)
        else:
            raise RuntimeError('Emulator did not boot within 240 seconds')
        def shell(*words):
            return run(adb + ['shell', *words])
        shell('input', 'keyevent', 'KEYCODE_WAKEUP')
        shell('wm', 'dismiss-keyguard')
        shell('settings', 'put', 'secure', 'show_ime_with_hard_keyboard', '1')
        shell('cmd', 'connectivity', 'airplane-mode', 'enable')
        shell('svc', 'wifi', 'disable')
        def baseline():
            shell('wm', 'size', '1080x1920')
            shell('wm', 'density', '420')
            shell('settings', 'put', 'system', 'font_scale', '1.0')
            shell('cmd', 'uimode', 'night', 'no')
            for setting in ('window_animation_scale', 'transition_animation_scale', 'animator_duration_scale'):
                shell('settings', 'put', 'global', setting, '0')
        baseline()
        print('Building APKs, JVM/lint and connected instrumentation…', flush=True)
        report['technical']['status'] = 'FAIL'
        save()
        build_started = time.time()
        build = run([ROOT / 'gradlew', '--no-daemon',
             '-Pandroid.testInstrumentationRunnerArguments.class=' + APP + '.SmokeTest', ':app:assembleDebug', ':app:assembleDebugAndroidTest',
             ':app:testDebugUnitTest', ':app:lintDebug', ':app:connectedDebugAndroidTest'], 'gradle', timeout=1500)
        (out / 'test-results').mkdir()
        for result in (ROOT / 'app/build/outputs/androidTest-results/connected/debug').rglob('TEST-*.xml'):
            if result.stat().st_mtime >= build_started:
                shutil.copy2(result, out / 'test-results' / result.name)
        check_instrumentation_results(out / 'test-results', report['serial'])
        report['technical'] = {'status': 'PASS', 'evidence': 'logs/gradle.txt',
            'jvm_tests': 'NOT_RUN' if ':app:testDebugUnitTest NO-SOURCE' in build.stdout else 'PASS'}
        for apk in (ROOT / 'app/build/outputs/apk/debug/app-debug.apk',
                    ROOT / 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'):
            run(adb + ['install', '-r', apk], 'install-' + apk.stem)
            shutil.copy2(apk, out / apk.name)
        report['apk_sha256'] = hashlib.sha256((out / 'app-debug.apk').read_bytes()).hexdigest()
        for scenario in SCENARIOS:
            print(f'Capturing {scenario}…', flush=True)
            baseline()
            shell('am', 'force-stop', APP)
            shell('pm', 'clear', APP)
            if scenario == 'dark':
                shell('cmd', 'uimode', 'night', 'yes')
            if scenario == 'small_screen':
                shell('wm', 'size', '720x1280')  # 360 x 640 dp
                shell('wm', 'density', '320')
            if scenario == 'large_text':
                shell('settings', 'put', 'system', 'font_scale', '2.0')
            remote = f'/sdcard/Android/data/{APP}/files/e2e/{scenario}.png'
            result = run(adb + ['shell', 'am', 'instrument', '-w', '-r', '-e', 'class',
                APP + '.VisualScenarioTest#capture', '-e', 'scenario', scenario, RUNNER],
                scenario, timeout=120)
            # am instrument can return zero even when a test crashes or an assertion fails.
            if not re.search(r'OK \(1 test\)', result.stdout) or 'FAILURES!!!' in result.stdout:
                report['captures'][scenario] = {'status': 'FAIL', 'evidence': f'logs/{scenario}.txt'}
                save()
                raise RuntimeError(f'Instrumentation failed for {scenario}; see logs/{scenario}.txt')
            path = out / 'screenshots' / f'{scenario}.png'
            run(adb + ['pull', remote, path], 'pull-' + scenario)
            if not path.read_bytes().startswith(b'\x89PNG\r\n\x1a\n'):
                raise RuntimeError(f'Invalid PNG for {scenario}')
            report['captures'][scenario] = {'status': 'PASS', 'path': f'screenshots/{scenario}.png',
                'sha256': hashlib.sha256(path.read_bytes()).hexdigest(), 'visual_status': 'NOT_RUN'}
            save()
        review = {s: {'status': 'NOT_RUN', 'sha256': report['captures'][s]['sha256'],
                      'observation': '', 'opened_with': ''} for s in SCENARIOS}
        review['_reference_comparison'] = {'status': 'NOT_RUN', 'opened_with': '',
                                            'observation': '', 'reference_sha256': {}}
        (out / 'visual-review.json').write_text(json.dumps(review, indent=2) + '\n')
        baseline()
        shell('am', 'force-stop', APP)
        shell('am', 'start', '-n', APP + '/.MainActivity')
        success = True
    except (OSError, RuntimeError, subprocess.TimeoutExpired, ET.ParseError) as error:
        report['error'] = redact(str(error))
        print(report['error'], file=sys.stderr)
    finally:
        save()
        if emulator and not (args.keep_emulator and success):
            if adb:
                try:
                    run(adb + ['emu', 'kill'], check=False, timeout=15)
                except (OSError, subprocess.TimeoutExpired):
                    pass
            try:
                emulator.wait(timeout=15)
            except subprocess.TimeoutExpired:
                emulator.kill()
                emulator.wait(timeout=10)
        if log:
            log.close()
            path = out / 'logs/emulator-local.txt'
            path.write_text(redact(path.read_text(errors='replace')))
        from report import render
        render(out)
        print(f'Evidence: {out}\nVisual review: NOT_RUN (open the images, then complete visual-review.json)', flush=True)
    return 0 if success else 1


if __name__ == '__main__':
    sys.exit(main())
