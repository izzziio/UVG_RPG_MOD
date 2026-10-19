"""Aplica una parte de la migración tras comprobar los archivos de partida."""
import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import subprocess
import sys

def digest(data):
    return hashlib.sha256(data).hexdigest()

def hashes(data):
    values = {digest(data)}
    try:
        text = data.decode('utf-8')
        if '\x00' not in text:
            values.add(digest(text.replace('\r\n', '\n').encode('utf-8')))
    except UnicodeDecodeError:
        pass
    return values

def safe_path(repo, relative):
    part = PurePosixPath(relative)
    if part.is_absolute() or '..' in part.parts or '.git' in part.parts or '\\' in relative:
        raise ValueError('Ruta no permitida: ' + relative)
    path = repo
    for name in part.parts:
        path = path / name
        if path.is_symlink():
            raise ValueError('No se modifican enlaces simbólicos: ' + relative)
    return path

def git(repo, *args):
    return subprocess.check_output(['git', '-C', str(repo), *args], stderr=subprocess.STDOUT).decode().strip()

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repo', required=True, help='Carpeta raíz del repositorio completo')
    parser.add_argument('--aplicar', action='store_true', help='Sin esta opción solo comprueba')
    args = parser.parse_args()
    package = Path(__file__).resolve().parent
    manifest = json.loads((package / 'manifiesto.json').read_text(encoding='utf-8'))
    repo = Path(args.repo).resolve()
    if Path(git(repo, 'rev-parse', '--show-toplevel')).resolve() != repo:
        raise ValueError('--repo debe apuntar a la raíz del repositorio Git.')
    if git(repo, 'status', '--porcelain', '--untracked-files=no'):
        raise ValueError('Hay cambios pendientes en archivos versionados. Guárdalos en un commit antes de continuar.')
    errors = []
    for relative, expected in manifest['antes'].items():
        path = safe_path(repo, relative)
        if not path.is_file() or not hashes(path.read_bytes()).intersection(expected):
            errors.append('No coincide con la versión de partida: ' + relative)
    for relative in manifest['despues']:
        path = safe_path(repo, relative)
        if relative not in manifest['antes'] and path.exists():
            errors.append('Ya existe una ruta que esta parte necesita crear: ' + relative)
        # No debe haber archivos donde se necesita una carpeta.
        for parent in path.parents:
            if parent == repo:
                break
            if parent.exists() and not parent.is_dir():
                errors.append('La carpeta de destino está ocupada por un archivo: ' + str(parent))
    for relative in manifest['escribir']:
        path = safe_path(package / 'archivos', relative)
        if not path.is_file() or digest(path.read_bytes()) != manifest['despues'][relative]['sha256']:
            errors.append('El paquete está incompleto o alterado: ' + relative)
    if errors:
        print('DETENIDO: no se cambió ningún archivo.\n' + '\n'.join(errors[:20]))
        print('Comprueba que usas la parte correcta y que las partes anteriores ya se integraron.')
        return 2
    print('Versión de partida compatible. Parte', manifest['parte'])
    print('Archivos a escribir:', len(manifest['escribir']), '| Archivos a retirar:', len(manifest['eliminar']))
    if not args.aplicar:
        print('Solo comprobación. Repite añadiendo --aplicar para efectuar los cambios.')
        return 0
    touched = sorted(set(manifest['escribir']) | set(manifest['eliminar']))
    backup = {p: safe_path(repo, p).read_bytes() if safe_path(repo, p).is_file() else None for p in touched}
    modes = {p: safe_path(repo, p).stat().st_mode if safe_path(repo, p).is_file() else None for p in touched}
    try:
        for relative in manifest['escribir']:
            path = safe_path(repo, relative)
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes((package / 'archivos' / relative).read_bytes())
            path.chmod(manifest['despues'][relative]['modo'])
        for relative in manifest['eliminar']:
            safe_path(repo, relative).unlink()
        for relative, expected in manifest['despues'].items():
            if not hashes(safe_path(repo, relative).read_bytes()).intersection(expected['hashes']):
                raise ValueError('Falló la verificación del resultado: ' + relative)
        for relative in manifest['eliminar']:
            if safe_path(repo, relative).exists():
                raise ValueError('No se retiró: ' + relative)
    except Exception:
        for relative in touched:
            path = safe_path(repo, relative)
            if backup[relative] is None:
                if path.is_file():
                    path.unlink()
            else:
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(backup[relative])
                path.chmod(modes[relative])
        raise
    print('Cambios aplicados y verificados. Revisa git status y git diff antes de confirmar.')
    print('Este programa no crea commits ni sube cambios a GitHub.')
    return 0

if __name__ == '__main__':
    try:
        sys.exit(main())
    except (ValueError, OSError, subprocess.CalledProcessError) as exc:
        print('DETENIDO:', str(exc))
        sys.exit(2)
