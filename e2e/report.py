#!/usr/bin/env python3
"""Render local evidence; PASS on capture never implies PASS on native visual review."""
import argparse
import hashlib
import html
import json
from pathlib import Path
from run import SCENARIOS


def visual_result(directory, capture, review):
    status = review.get('status', 'NOT_RUN')
    if status not in ('PASS', 'FAIL'):
        return 'NOT_RUN', 'Image non examinée'
    path = directory / capture.get('path', 'missing')
    if not path.is_file() or not path.resolve().is_relative_to(directory.resolve()):
        return 'NOT_RUN', 'Preuve absente ou hors du dossier'
    digest = hashlib.sha256(path.read_bytes()).hexdigest()
    if digest != capture.get('sha256') or digest != review.get('sha256'):
        return 'NOT_RUN', 'Capture modifiée depuis la revue'
    if not review.get('opened_with', '').strip() or not review.get('observation', '').strip():
        return 'NOT_RUN', 'Outil vision et observation motivée requis'
    if capture.get('status') != 'PASS':
        return 'NOT_RUN', 'Scénario technique non validé'
    return status, review['observation']


def render(directory):
    report = json.loads((directory / 'report.json').read_text())
    review_path = directory / 'visual-review.json'
    reviews = json.loads(review_path.read_text()) if review_path.exists() else {}
    rows = []
    cards = []
    outcomes = {}
    for scenario in SCENARIOS:
        capture = report['captures'][scenario]
        status, observation = visual_result(directory, capture, reviews.get(scenario, {}))
        outcomes[scenario] = status
        rows.append(f'| {scenario} | {capture["status"]} | {status} | {observation.replace(chr(10), " ").replace("|", "/")} |')
        if capture.get('path'):
            source = html.escape(capture['path'], quote=True)
            cards.append(f'<figure><a href="{source}"><img src="{source}" alt="{scenario}"></a>'
                         f'<figcaption>{scenario}: {status}<p>{html.escape(observation)}</p></figcaption></figure>')
    normal = [outcomes[s] for s in SCENARIOS if s != 'regression_fixture']
    visual = 'FAIL' if 'FAIL' in normal else ('PASS' if all(s == 'PASS' for s in normal) else 'NOT_RUN')
    # The deliberately bad image must FAIL visual review to prove detection.
    fixture = outcomes['regression_fixture']
    regression = 'PASS' if fixture == 'FAIL' else ('FAIL' if fixture == 'PASS' else 'NOT_RUN')
    reference = reviews.get('_reference_comparison', {})
    reference_status = 'NOT_RUN'
    manifest_path = directory / 'references/manifest.json'
    if manifest_path.exists() and reference.get('status') in ('PASS', 'FAIL'):
        manifest = json.loads(manifest_path.read_text())
        valid = bool(reference.get('opened_with', '').strip() and reference.get('observation', '').strip())
        for name, digest in manifest['images'].items():
            path = directory / 'references' / name
            valid = valid and path.is_file() and hashlib.sha256(path.read_bytes()).hexdigest() == digest
            valid = valid and reference.get('reference_sha256', {}).get(name) == digest
        if valid and all(value != 'NOT_RUN' for value in normal):
            reference_status = reference['status']
    report['visual_review'] = {'status': visual, 'evidence': 'visual-review.json'}
    report['reference_comparison'] = {'status': reference_status, 'evidence': 'visual-review.json'}
    report['regression_detection'] = {'status': regression, 'evidence': 'visual-review.json'}
    for name, value in outcomes.items():
        report['captures'][name]['visual_status'] = value
    (directory / 'report.json').write_text(json.dumps(report, indent=2, ensure_ascii=False) + '\n')
    text = f'''# Issue #54 — tentative {report['attempt']}/3

Commit : `{report['commit']}` ; modifications locales : {report['working_tree_dirty']}.
Android API {report['api']} ; émulateur `{report['serial']}` ; données synthétiques.

| Critère | Statut |
| --- | --- |
| Tâches Gradle build/lint et instrumentation Compose | {report['technical']['status']} |
| Tests JVM réellement exécutés | {report['technical'].get('jvm_tests', 'NOT_RUN')} |
| Revue visuelle native des états normaux | {visual} |
| Détection du défaut volontaire | {regression} |
| Comparaison aux références Eta sélectionnées par l’utilisateur | {reference_status} |
| Appareil Samsung / retour tactile physique | NOT_RUN |

Références : Eta, commit `4101bc700be7c13b7b72f85a59c1c320f2183c04`,
chat_home.jpg et settings.jpg (sources et empreintes dans references/manifest.json).
La comparaison porte sur les états communs du prototype ; elle ne prouve pas
la parité fonctionnelle avec Eta. La CI ne juge pas l'UX.

Observation comparative : {reference.get('observation', 'NOT_RUN')}

| État | Capture et assertions | Revue visuelle | Observation |
| --- | --- | --- | --- |
''' + '\n'.join(rows) + '\n'
    if report.get('error'):
        text += '\nErreur : ' + report['error'] + '\n'
    (directory / 'report.md').write_text(text)
    (directory / 'index.html').write_text('''<!doctype html><html lang="fr"><meta charset="utf-8">
<title>Jean-Calcul — preuves E2E</title><style>
body{font:16px system-ui;margin:24px;background:#eee;color:#161616}
main{display:flex;flex-wrap:wrap;gap:24px}figure{margin:0;width:300px}
img{width:100%;height:auto}figcaption{padding:12px 0}p{max-width:70ch}
</style><h1>Jean-Calcul — preuves E2E</h1>
<p>Galerie locale de données synthétiques. Ouvrir chaque PNG avec la vision native de l'agent.
La présence d'une image n'atteste pas sa revue.</p><p><a href="report.md">Rapport</a></p><main>'''
        + ''.join(cards) + '</main></html>')
    return visual, regression


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directory', type=Path)
    args = parser.parse_args()
    print('Revue / détection :', *render(args.directory))
