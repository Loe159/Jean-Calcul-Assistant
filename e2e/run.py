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
import threading
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
APP = 'fr.loevan.jeancalcul.v2'
RUNNER = APP + '.test/androidx.test.runner.AndroidJUnitRunner'
SCENARIOS = ('empty', 'message', 'reset', 'settings', 'dark', 'small_screen',
             'large_text', 'keyboard', 'reduced_motion', 'menu', 'providers', 'tools', 'appearance', 'regression_fixture')


def redact(text):
    text = re.sub(r'(?i)(bearer\s+|(?:api[_-]?key|token|password|secret)\s*[=:]\s*)[^\s,;]+', r'\1[REDACTED]', text)
    text = re.sub(r'\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b', '[EMAIL]', text)
    return text.replace(str(Path.home()), '[HOME]')


def complete_png(data):
    return data.startswith(b'\x89PNG\r\n\x1a\n') and data.endswith(
        b'\x00\x00\x00\x00IEND\xaeB\x60\x82')


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


def require_gradle_tasks(result, tasks):
    """A started task or an old APK is not evidence of a completed build."""
    if result.returncode:
        raise RuntimeError(f'Gradle failed ({result.returncode}); see the build log')
    for task in tasks:
        if not re.search(rf'> Task :app:{re.escape(task)}(?: UP-TO-DATE| FROM-CACHE)?(?:\r?\n|$)', result.stdout):
            raise RuntimeError(f'{task} did not complete; refusing stale evidence')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--run-id', default=dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%SZ'))
    parser.add_argument('--attempt', type=int, default=1)
    parser.add_argument('--extra-validation-authorized', action='store_true',
                        help='Attempts above 3 require explicit user authorization to exceed the budget')
    parser.add_argument('--authorization-note', help='Record the explicit user instruction authorizing additional attempts')
    parser.add_argument('--software-emulator', action='store_true', help='Use software CPU emulation when KVM is unavailable')
    parser.add_argument('--show-window', action='store_true')
    parser.add_argument('--keep-emulator', action='store_true', help='Leave the synthetic AVD open for interaction')
    parser.add_argument('--port', type=int, default=5580)
    args = parser.parse_args()
    if args.attempt < 1:
        parser.error('Attempt must be positive')
    if args.attempt > 3 and not args.extra_validation_authorized:
        parser.error('Attempts above 3 require explicit user authorization and --extra-validation-authorized')
    if args.attempt > 4 and not args.authorization_note:
        parser.error('Attempts above 4 require --authorization-note recording the explicit user instruction')
    if not re.fullmatch(r'[A-Za-z0-9_-]+', args.run_id):
        parser.error('run-id must contain only letters, numbers, underscore or dash')
    if args.port < 5554 or args.port > 5682 or args.port % 2:
        parser.error('port must be even, between 5554 and 5682')
    out = ROOT / 'e2e-artifacts' / args.run_id / f'attempt-{args.attempt}'
    out.mkdir(parents=True, exist_ok=False)  # Never overwrite evidence or silently retry.
    (out / 'screenshots').mkdir()
    (out / 'logs').mkdir()
    report = {'issue': int(re.sub(r'\D', '', args.run_id) or '54'), 'attempt': args.attempt,
              'attempt_budget': max(3, args.attempt),
              'budget_override': (args.authorization_note or 'Explicit user authorization, 2026-11-12') if args.attempt > 3 else None, 'created_utc': dt.datetime.now(dt.timezone.utc).isoformat(),
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
    monitor_stop = threading.Event()
    monitor_thread = None
    app_log_process = None
    app_log_stream = None
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
        # Full Android includes SystemUI and IME; ATD disables rendering and cannot validate screenshots.
        image = 'system-images;android-35;google_apis;x86_64'
        report['system_image'] = image
        run([avdmanager, 'create', 'avd', '-n', name, '-k', image, '--device', 'pixel_6'],
            'create-avd', input='no\n')
        # Pixel 6 defaults to 12 GiB of userdata. This account-free fixture needs
        # much less; size only our new disposable AVD, never an existing device.
        config = out / 'avd' / f'{name}.avd' / 'config.ini'
        display_config = {'disk.dataPartition.size': '4G', 'hw.lcd.width': '720',
                          'hw.lcd.height': '1280', 'hw.lcd.density': '280', 'hw.ramSize': '4096'}
        config.write_text('\n'.join(line for line in config.read_text().splitlines()
                                    if line.split('=', 1)[0] not in display_config)
                          + '\n' + '\n'.join(f'{key}={value}' for key, value in display_config.items()) + '\n')
        # Same 411 x 731 dp viewport as 1080 x 1920 @ 420 dpi, with 56% fewer pixels.
        report['display'] = {'pixels': [720, 1280], 'density': 280, 'dp': [411, 731]}
        # Compile and run JVM/lint before the emulator consumes memory. In particular,
        # dexing/lint must not compete with Android on small CI runners.
        print('Building APKs and running JVM/lint before starting Android…', flush=True)
        gradle_base = [ROOT / 'gradlew', '-Pjeancalcul.emulator=true', '--no-daemon',
            '--no-configuration-cache', '--max-workers=1',
            '-Dorg.gradle.jvmargs=-Xmx2500m -XX:MaxMetaspaceSize=768m -Dfile.encoding=UTF-8']
        build_tasks = ('assembleDebug', 'assembleDebugAndroidTest', 'testDebugUnitTest', 'lintDebug')
        report['technical'].update(status='FAIL', build='RUNNING', jvm_tests='NOT_RUN', lint='NOT_RUN')
        save()
        build = run(gradle_base + [':app:' + task for task in build_tasks],
                    'gradle-build', timeout=1500, check=False)
        require_gradle_tasks(build, build_tasks)
        for apk in (ROOT / 'app/build/outputs/apk/debug/app-debug.apk',
                    ROOT / 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'):
            shutil.copy2(apk, out / apk.name)
        report['apk_sha256'] = hashlib.sha256((out / 'app-debug.apk').read_bytes()).hexdigest()
        report['technical'].update(status='NOT_RUN', build='PASS', jvm_tests='PASS', lint='PASS',
                                   evidence='logs/gradle-build.txt', instrumentation='NOT_RUN')
        save()
        log = (out / 'logs/emulator-local.txt').open('w')
        cmd = [emulator_bin, '-avd', name, '-port', str(args.port), '-wipe-data', '-no-snapshot',
               '-no-metrics', '-no-audio', '-no-boot-anim', '-gpu', 'swiftshader',
               '-feature', '-Vulkan', '-skin', '720x1280', '-memory', '4096', '-cores', '2']
        if args.software_emulator:
            env['ANDROID_I_WANT_MY_TCG'] = 'yes'
            cmd += ['-accel', 'off']
        if not args.show_window:
            cmd.append('-no-window')
        emulator = subprocess.Popen(cmd, env=env, stdout=log, stderr=subprocess.STDOUT)
        health = {'samples': 0, 'peak_emulator_rss_kb': 0, 'min_host_available_kb': None}
        report['emulator_health'] = health
        def monitor_emulator():
            while not monitor_stop.is_set():
                try:
                    status = Path(f'/proc/{emulator.pid}/status').read_text()
                    rss = re.search(r'^VmRSS:\s+(\d+)', status, re.MULTILINE)
                    available = re.search(r'^MemAvailable:\s+(\d+)', Path('/proc/meminfo').read_text(), re.MULTILINE)
                    health['samples'] += 1
                    if rss:
                        health['peak_emulator_rss_kb'] = max(health['peak_emulator_rss_kb'], int(rss.group(1)))
                    if available:
                        value = int(available.group(1))
                        health['min_host_available_kb'] = value if health['min_host_available_kb'] is None else min(value, health['min_host_available_kb'])
                except OSError:
                    pass
                if emulator.poll() is not None:
                    health['unexpected_exit_code'] = emulator.returncode
                    break
                monitor_stop.wait(2)
        monitor_thread = threading.Thread(target=monitor_emulator, daemon=True)
        monitor_thread.start()

        adb = [adb_bin, '-s', report['serial']]
        deadline = time.monotonic() + (600 if args.software_emulator else 240)
        while time.monotonic() < deadline:
            if emulator.poll() is not None:
                raise RuntimeError('Emulator exited; see logs/emulator-local.txt')
            try:
                boot = run(adb + ['shell', 'getprop', 'sys.boot_completed'], check=False, timeout=15)
            except subprocess.TimeoutExpired:
                time.sleep(1)
                continue
            if boot.stdout.strip() == '1':
                break
            time.sleep(2)
        else:
            raise RuntimeError('Emulator did not finish booting within the configured deadline')
        def shell(*words):
            return run(adb + ['shell', *words])
        shell('input', 'keyevent', 'KEYCODE_WAKEUP')
        shell('wm', 'dismiss-keyguard')
        shell('settings', 'put', 'secure', 'show_ime_with_hard_keyboard', '1')
        shell('cmd', 'connectivity', 'airplane-mode', 'enable')
        shell('svc', 'wifi', 'disable')
        def baseline():
            shell('wm', 'size', '720x1280')
            shell('wm', 'density', '280')
            shell('settings', 'put', 'system', 'font_scale', '1.0')
            shell('cmd', 'uimode', 'night', 'no')
            for setting in ('window_animation_scale', 'transition_animation_scale', 'animator_duration_scale'):
                shell('settings', 'put', 'global', setting, '0')
        baseline()
        # Gradle has exited. Keep the emulator's test phase free of another build JVM.
        for apk in (out / 'app-debug.apk', out / 'app-debug-androidTest.apk'):
            run(adb + ['install', '-r', apk], 'install-' + apk.stem)
        # Capture only this synthetic application's UID, never global Android logs.
        uid_output = run(adb + ['shell', 'pm', 'list', 'packages', '-U', APP],
                         'target-app-uid', timeout=15).stdout
        uid = re.search(r'package:' + re.escape(APP) + r'\s+uid:(\d+)', uid_output)
        if not uid:
            raise RuntimeError('Could not resolve the synthetic application UID for scoped diagnostics')
        app_log_stream = (out / 'logs/app-logcat.txt').open('w')
        app_log_process = subprocess.Popen(adb + ['logcat', '--uid=' + uid.group(1),
            '-v', 'threadtime', '-T', '1'], cwd=ROOT, env=env,
            stdout=app_log_stream, stderr=subprocess.STDOUT)
        guest_memory = run(adb + ['shell', 'cat', '/proc/meminfo'], 'guest-memory', timeout=15)
        available = re.search(r'^MemAvailable:\s+(\d+)', guest_memory.stdout, re.MULTILINE)
        report['guest_available_kb_before_test'] = int(available.group(1)) if available else None
        report['guest_ram_mb'] = 4096
        save()
        print('Running Android instrumentation directly on the owned emulator…', flush=True)
        instrumentation_started = time.time()
        instrumented = run(adb + ['shell', 'am', 'instrument', '-w', '-r', '-e', 'class',
            APP + '.SmokeTest', RUNNER], 'instrumentation-smoke', timeout=180, check=False)
        (out / 'test-results').mkdir()
        technical_error = None
        try:
            from instrumentation import write_results
            write_results(instrumented, out / 'test-results/TEST-smoke.xml', report['serial'], {
                (APP + '.SmokeTest', 'localVllmWithoutKeyStreamsAndPersistsARealConversation'),
                (APP + '.SmokeTest', 'settingsShowOnlyTheSelectedConversationProviders'),
            })
            check_instrumentation_results(out / 'test-results', report['serial'], since=instrumentation_started)
        except RuntimeError as error:
            technical_error = redact(str(error))
        report['technical'].update(status='FAIL' if technical_error else 'PASS',
            instrumentation='FAIL' if technical_error else 'PASS', evidence='logs/instrumentation-smoke.txt')
        if technical_error:
            report['error'] = technical_error
            report['technical']['error'] = technical_error
            print('Technical failure retained; collecting visual scenarios independently…', flush=True)
        save()
        for scenario in SCENARIOS:
            print(f'Capturing {scenario}…', flush=True)
            shell('pm', 'clear', APP)
            baseline()
            shell('am', 'force-stop', APP)
            # Each scenario owns fresh synthetic Room/DataStore state.
            if scenario == 'dark':
                shell('cmd', 'uimode', 'night', 'yes')
            if scenario == 'small_screen':
                shell('wm', 'size', '720x1280')  # 360 x 640 dp
                shell('wm', 'density', '320')
            if scenario == 'large_text':
                shell('settings', 'put', 'system', 'font_scale', '2.0')
            remote = f'files/e2e/{scenario}.png'
            result = run(adb + ['shell', 'am', 'instrument', '-w', '-r', '-e', 'class',
                APP + '.VisualScenarioTest#capture', '-e', 'scenario', scenario, RUNNER],
                scenario, timeout=120)
            # am instrument can return zero even when a test crashes or an assertion fails.
            if not re.search(r'OK \(1 test\)', result.stdout) or 'FAILURES!!!' in result.stdout:
                report['captures'][scenario] = {'status': 'FAIL', 'evidence': f'logs/{scenario}.txt'}
                # Diagnostic evidence belongs to this failed scenario; it cannot pass review.
                for suffix in ('png', 'xml'):
                    try:
                        diagnostic = subprocess.run(adb + ['exec-out', 'run-as', APP, 'cat',
                            f'files/e2e/failure-{scenario}.{suffix}'], cwd=ROOT, env=env,
                            capture_output=True, timeout=15)
                        if diagnostic.returncode == 0 and diagnostic.stdout:
                            target = out / 'screenshots' / f'failure-{scenario}.{suffix}'
                            target.write_bytes(diagnostic.stdout)
                            report['captures'][scenario]['diagnostic_' + suffix] = str(target.relative_to(out))
                    except subprocess.TimeoutExpired:
                        pass
                save()
                raise RuntimeError(f'Instrumentation failed for {scenario}; see logs/{scenario}.txt')
            path = out / 'screenshots' / f'{scenario}.png'
            transfer_log = []
            data = b''
            # Retry reads of the same immutable file, never a failed scenario.
            for transfer_attempt in range(1, 4):
                transfer = subprocess.run(adb + ['exec-out', 'run-as', APP, 'cat', remote],
                    cwd=ROOT, env=env, capture_output=True, timeout=30)
                valid = transfer.returncode == 0 and complete_png(transfer.stdout)
                transfer_log.append(f'try={transfer_attempt} exit={transfer.returncode} '
                    f'bytes={len(transfer.stdout)} complete_png={valid}\n' +
                    redact(transfer.stderr.decode(errors='replace')))
                (out / 'logs' / f'transfer-{scenario}.txt').write_text('\n'.join(transfer_log))
                if valid:
                    data = transfer.stdout
                    break
                if transfer_attempt < 3:
                    time.sleep(0.5)
            if not data:
                report['captures'][scenario] = {'status': 'FAIL', 'evidence': f'logs/transfer-{scenario}.txt'}
                raise RuntimeError(f'No complete PNG for {scenario}; see logs/transfer-{scenario}.txt')
            path.write_bytes(data)
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
        shell('am', 'start', '-n', APP + '/io.github.mangi.eta.ui.MainActivity')
        success = technical_error is None
    except (OSError, RuntimeError, subprocess.TimeoutExpired, ET.ParseError) as error:
        report['error'] = redact(str(error))
        print(report['error'], file=sys.stderr)
    finally:
        if app_log_process:
            report['app_log_exit_before_cleanup'] = app_log_process.poll()
            if app_log_process.poll() is None:
                app_log_process.terminate()
                try:
                    app_log_process.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    app_log_process.kill()
                    app_log_process.wait(timeout=5)
        if app_log_stream:
            app_log_stream.close()
            app_log_path = out / 'logs/app-logcat.txt'
            app_log_path.write_text(redact(app_log_path.read_text(errors='replace')))
        monitor_stop.set()
        if monitor_thread:
            monitor_thread.join(timeout=3)
        if emulator:
            report['emulator_exit_before_cleanup'] = emulator.poll()
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
