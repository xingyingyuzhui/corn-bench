"""Check built APKs: the local RTU bridge is debug-only and both variants are offline."""
from pathlib import Path
import zipfile


def verify(root=Path('.')):
    bridge = b'modbus-mobile-rtu-lab'
    for variant, filename in [('debug', 'app-debug.apk'), ('release', 'app-release-unsigned.apk')]:
        apk = root / 'app/build/outputs/apk' / variant / filename
        with zipfile.ZipFile(apk) as archive:
            dex = b''.join(archive.read(name) for name in archive.namelist() if name.endswith('.dex'))
            assert (bridge in dex) == (variant == 'debug'), f'{variant}: unexpected local serial bridge contents'
            manifest = archive.read('AndroidManifest.xml')
            permission = 'android.permission.INTERNET'
            assert not any(permission.encode(encoding) in manifest for encoding in ('utf-8', 'utf-16le')), f'{variant}: INTERNET permission found'
        print(f'{variant}: local bridge isolation and offline manifest verified')


if __name__ == '__main__':
    verify()
