"""Convert AndroidJUnitRunner's per-test status protocol into retained JUnit evidence."""
import re
import xml.etree.ElementTree as ET


def write_results(result, destination, serial, expected_tests):
    fields, records = {}, []
    for line in result.stdout.splitlines():
        if line.startswith('INSTRUMENTATION_STATUS: '):
            key, separator, value = line[len('INSTRUMENTATION_STATUS: '):].partition('=')
            if separator:
                fields[key] = value
        elif line.startswith('INSTRUMENTATION_STATUS_CODE: '):
            code = int(line.rsplit(':', 1)[1].strip())
            if code != 1 and fields.get('class') and fields.get('test'):
                records.append((fields['class'], fields['test'], code, fields.get('stack', '')))
            fields = {}
    suite = ET.Element('testsuite', name='AndroidJUnitRunner', tests=str(len(records)),
        failures=str(sum(code in (-1, -2) for _, _, code, _ in records)),
        skipped=str(sum(code in (-3, -4) for _, _, code, _ in records)), errors='0')
    props = ET.SubElement(suite, 'properties')
    ET.SubElement(props, 'property', name='device', value=serial)
    for classname, name, code, stack in records:
        case = ET.SubElement(suite, 'testcase', classname=classname, name=name)
        if code in (-3, -4):
            ET.SubElement(case, 'skipped').text = stack or f'Android runner status {code}'
        elif code != 0:
            ET.SubElement(case, 'failure').text = stack or f'Android runner status {code}'
    destination.parent.mkdir(parents=True, exist_ok=True)
    ET.ElementTree(suite).write(destination, encoding='utf-8', xml_declaration=True)
    observed = [(classname, name) for classname, name, _, _ in records]
    summary = re.search(r'OK \((\d+) tests?\)', result.stdout)
    normal_finish = re.search(r'^INSTRUMENTATION_CODE: -1\s*$', result.stdout, re.MULTILINE)
    if (result.returncode or len(observed) != len(expected_tests)
            or set(observed) != set(expected_tests) or any(code != 0 for _, _, code, _ in records)
            or not summary or int(summary.group(1)) != len(expected_tests) or not normal_finish
            or 'FAILURES!!!' in result.stdout):
        raise RuntimeError('Android instrumentation did not complete every expected test successfully; see its log and JUnit XML')
    return destination
