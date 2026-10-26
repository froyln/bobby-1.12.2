#!/usr/bin/env python3
import os
import sys
import tempfile
from pathlib import Path
from zipfile import ZIP_STORED, ZipFile, ZipInfo

artifact = Path(sys.argv[1])
permissions = artifact.stat().st_mode & 0o777
fd, temporary = tempfile.mkstemp(prefix=artifact.name + '.', dir=str(artifact.parent))
os.close(fd)
try:
    with ZipFile(artifact) as source, ZipFile(temporary, 'w') as result:
        for original in sorted(source.infolist(), key=lambda item: item.filename):
            entry = ZipInfo(original.filename, (1980, 1, 1, 0, 0, 0))
            entry.compress_type = ZIP_STORED
            entry.create_system = 3
            entry.external_attr = (0o755 << 16) | 0x10 if original.is_dir() else 0o644 << 16
            result.writestr(entry, source.read(original))
    os.chmod(temporary, permissions)
    os.replace(temporary, artifact)
finally:
    if os.path.exists(temporary):
        os.unlink(temporary)
