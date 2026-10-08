#!/usr/bin/env python3
"""Audited 9-source native prefetch; network access only during --prepare.

F-Droid/CI workflow: prepare pinned sources outside of the actual build
sandbox, then verify them and invoke CMake/Gradle with offline mode enabled.
This script is NOT evidence of a completed fdroidserver release.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile

REPO = Path(__file__).resolve().parents[2]
LOCK = REPO / "native/git/fdroid-sources.lock.json"
CMAKE = REPO / "native/git/CMakeLists.txt"
PATCH = REPO / "native/git/cmake/libgit2-no-install.patch"
SHA_RE = re.compile(r"^[0-9a-f]{40}$")
NAME_RE = re.compile(r"^[a-z][a-z0-9_]*$")
URL_RE = re.compile(r"^https://github\.com/[a-zA-Z0-9_.-]+/[a-zA-Z0-9_.-]+\.git$")


def run(args: list[str], *, timeout: int = 300) -> str:
    env = dict(os.environ, GIT_TERMINAL_PROMPT="0", GIT_ASKPASS="/bin/false")
    proc = subprocess.run(args, text=True, capture_output=True, env=env,
                          timeout=timeout, check=False)
    if proc.returncode:
        # Do not echo arbitrary upstream output (credentials/URLs in logs).
        raise RuntimeError(f"{args[0]} {args[1] if len(args)>1 else ''}: exit {proc.returncode}")
    return proc.stdout.strip()


def load_lock() -> list[dict[str, str]]:
    data = json.loads(LOCK.read_text(encoding="utf-8"))
    sources = data.get("dependencies")
    if data.get("schemaVersion") != 1 or not isinstance(sources, list) or len(sources) != 9:
        raise ValueError("Native F-Droid lock must contain exactly nine sources, schema v1")
    cmake = CMAKE.read_text(encoding="utf-8")
    names: set[str] = set()
    variables: set[str] = set()
    for item in sources:
        if set(item) != {"name", "url", "commit", "cmakeVariable"}:
            raise ValueError("Unexpected lockfile source fields")
        name, url, commit, var = (item[k] for k in ("name","url","commit","cmakeVariable"))
        if not isinstance(name, str) or not NAME_RE.fullmatch(name) or name in names:
            raise ValueError("Invalid/duplicate source folder")
        if not isinstance(url, str) or not URL_RE.fullmatch(url):
            raise ValueError(f"Invalid upstream URL: {name}")
        if not isinstance(commit, str) or not SHA_RE.fullmatch(commit):
            raise ValueError(f"Invalid revision: {name}")
        if not isinstance(var, str) or not re.fullmatch(r"NEXORA_[A-Z_]+_REF", var) or var in variables:
            raise ValueError(f"Invalid/duplicate CMake ref var: {name}")
        pattern = r"set\(\s*" + re.escape(var) + r'\s+"([0-9a-f]{40})"'
        m = re.search(pattern, cmake)
        if not m or m.group(1) != commit:
            raise ValueError(f"Lockfile revision differs from CMake: {name}")
        names.add(name)
        variables.add(var)
    return sources


def check_one(path: Path, entry: dict[str,str]) -> None:
    if not path.is_dir() or path.is_symlink():
        raise ValueError(f"Missing or symlinked source: {entry['name']}")
    actual = run(["git","-C",str(path),"rev-parse","HEAD"])
    if actual != entry["commit"]:
        raise ValueError(f"Revision mismatch: {entry['name']}")
    origin = run(["git","-C",str(path),"remote","get-url","origin"])
    if origin != entry["url"]:
        raise ValueError(f"Upstream URL mismatch: {entry['name']}")
    if entry["name"] == "libgit2":
        # An offline override bypasses CMake's normal FetchContent PATCH_COMMAND.
        run(["git","-C",str(path),"apply","--reverse","--check",str(PATCH)])
    elif run(["git","-C",str(path),"status","--porcelain"]):
        raise ValueError(f"Unexpected dirty checkout: {entry['name']}")


def prepare(root: Path, entry: dict[str,str]) -> None:
    dest = root / entry["name"]
    if dest.exists():
        check_one(dest, entry)
        return
    # Never allow arbitrary repo URLs, shell strings or moving tags from args.
    staging = Path(tempfile.mkdtemp(prefix=".nexora-pinned-", dir=root))
    try:
        # Partial clone still fetches the actual pinned tree; git protocol's
        # reachability controls are honored. Full history is not always needed.
        run(["git","clone","--filter=blob:none","--no-checkout","--no-tags",
             entry["url"],str(staging)],timeout=900)
        run(["git","-C",str(staging),"checkout","--detach",entry["commit"]],timeout=900)
        if entry["name"] == "libgit2":
            run(["git","-C",str(staging),"apply","--whitespace=nowarn",str(PATCH)])
        check_one(staging, entry)
        # Atomic after successful verification; never leave a half-clone.
        staging.rename(dest)
    finally:
        if staging.exists():
            shutil.rmtree(staging)


def main() -> int:
    parser=argparse.ArgumentParser(description=__doc__)
    group=parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--prepare",action="store_true",help="Networked source acquisition only")
    group.add_argument("--verify-only",action="store_true",help="Offline validation; never clone")
    parser.add_argument("--source-root",type=Path,required=True)
    parser.add_argument("--report",type=Path)
    args=parser.parse_args()
    try:
        root=args.source_root.resolve()
        if root == REPO or root == Path("/") or REPO in root.parents:
            raise ValueError("Use an external, isolated native source root")
        sources=load_lock()
        if args.prepare:
            root.mkdir(parents=True,exist_ok=True)
        if not root.is_dir():
            raise ValueError("Source root does not exist")
        for entry in sources:
            if args.prepare:
                prepare(root,entry)
            check_one(root / entry["name"],entry)
        if args.report:
            report={
                "schemaVersion":1,
                "sourceLockSha256":hashlib.sha256(LOCK.read_bytes()).hexdigest(),
                "libgit2PatchSha256":hashlib.sha256(PATCH.read_bytes()).hexdigest(),
                "sources":[{"name":e["name"],"origin":e["url"],"commit":e["commit"]}
                           for e in sources],
                "status":"nine-pinned-source-checkouts-verified",
                "releaseQualified":False,
            }
            args.report.parent.mkdir(parents=True,exist_ok=True)
            args.report.write_text(json.dumps(report,indent=2,sort_keys=True)+"\n",encoding="utf-8")
        print(f"Verified {len(sources)} pinned native source checkouts (mode={'prepare' if args.prepare else 'offline verify'})")
    except (RuntimeError,ValueError,OSError,subprocess.TimeoutExpired,json.JSONDecodeError) as error:
        print(f"F-Droid source verification failed: {error}",file=sys.stderr)
        return 1
    return 0


if __name__=="__main__":
    raise SystemExit(main())
