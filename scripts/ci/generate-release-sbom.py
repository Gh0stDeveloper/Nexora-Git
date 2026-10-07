#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import json
import os
import re
import sys
import time
import tomllib
from pathlib import Path
from urllib.parse import quote

ROOT = Path(__file__).resolve().parents[2]


def spdx_id(value: str) -> str:
    cleaned = re.sub(r"[^A-Za-z0-9.-]+", "-", value).strip("-.")
    return "SPDXRef-" + (cleaned or "package")


def add_package(packages, relationships, seen, *, name, version, purl=None, download="NOASSERTION", supplier="NOASSERTION"):
    key = (name, version, purl or "")
    if key in seen:
        return
    seen.add(key)
    pid = spdx_id(f"{name}-{version}-{len(packages)}")
    external = []
    if purl:
        external.append({
            "referenceCategory": "PACKAGE-MANAGER",
            "referenceType": "purl",
            "referenceLocator": purl,
        })
    packages.append({
        "SPDXID": pid,
        "name": name,
        "versionInfo": version,
        "downloadLocation": download,
        "filesAnalyzed": False,
        "licenseConcluded": "NOASSERTION",
        "licenseDeclared": "NOASSERTION",
        "copyrightText": "NOASSERTION",
        "supplier": supplier,
        "externalRefs": external,
    })
    relationships.append({
        "spdxElementId": "SPDXRef-Package-NexoraGit",
        "relationshipType": "DEPENDS_ON",
        "relatedSpdxElement": pid,
    })


def gradle_packages(packages, relationships, seen):
    report = ROOT / "build/reports/release-runtime-dependencies.txt"
    if report.is_file():
        pattern = re.compile(r"--- ([A-Za-z0-9_.-]+):([A-Za-z0-9_.-]+):([^\s()]+)")
        for line in report.read_text(errors="ignore").splitlines():
            match = pattern.search(line)
            if not match:
                continue
            group, artifact, version = match.groups()
            if " -> " in version:
                version = version.rsplit("->", 1)[-1].strip()
            add_package(
                packages, relationships, seen,
                name=f"{group}:{artifact}",
                version=version,
                purl=f"pkg:maven/{quote(group, safe='')}/{quote(artifact, safe='')}@{quote(version, safe='')}",
            )
        return

    catalog = ROOT / "gradle/libs.versions.toml"
    data = tomllib.loads(catalog.read_text())
    versions = data.get("versions", {})
    for alias, item in data.get("libraries", {}).items():
        module = item.get("module")
        if not module:
            continue
        version = item.get("version")
        if isinstance(version, dict):
            version = versions.get(version.get("ref", ""))
        if not version:
            continue
        group, artifact = module.split(":", 1)
        add_package(
            packages, relationships, seen,
            name=module,
            version=str(version),
            purl=f"pkg:maven/{quote(group, safe='')}/{quote(artifact, safe='')}@{quote(str(version), safe='')}",
        )


def npm_packages(packages, relationships, seen):
    lock = ROOT / "web/package-lock.json"
    if not lock.is_file():
        return
    data = json.loads(lock.read_text())
    for path, item in data.get("packages", {}).items():
        if not path.startswith("node_modules/"):
            continue
        name = path[len("node_modules/"):]
        version = str(item.get("version", "")).strip()
        if not version:
            continue
        add_package(
            packages, relationships, seen,
            name=name,
            version=version,
            purl=f"pkg:npm/{quote(name, safe='@/')}@{quote(version, safe='')}",
            download=item.get("resolved", "NOASSERTION"),
        )


def go_packages(packages, relationships, seen):
    go_mod = ROOT / "auth-broker/go.mod"
    if not go_mod.is_file():
        return
    text = go_mod.read_text()
    in_require = False
    for raw in text.splitlines():
        line = raw.strip()
        if line == "require (":
            in_require = True
            continue
        if in_require and line == ")":
            in_require = False
            continue
        if line.startswith("require "):
            line = line[len("require "):].strip()
        elif not in_require:
            continue
        line = line.split("//", 1)[0].strip()
        parts = line.split()
        if len(parts) < 2:
            continue
        module, version = parts[0], parts[1]
        add_package(
            packages, relationships, seen,
            name=module,
            version=version,
            purl=f"pkg:golang/{quote(module, safe='/')}@{quote(version, safe='')}",
            download=f"https://proxy.golang.org/{module}/@v/{version}.zip",
        )


def native_packages(packages, relationships, seen):
    cmake = (ROOT / "native/git/CMakeLists.txt").read_text()
    mappings = {
        "NEXORA_LIBGIT2_REF": ("libgit2", "https://github.com/libgit2/libgit2"),
        "NEXORA_MBEDTLS_REF": ("mbedtls", "https://github.com/Mbed-TLS/mbedtls"),
        "NEXORA_TREE_SITTER_REF": ("tree-sitter", "https://github.com/tree-sitter/tree-sitter"),
        "NEXORA_TREE_SITTER_KOTLIN_REF": ("tree-sitter-kotlin", "https://github.com/fwcd/tree-sitter-kotlin"),
        "NEXORA_TREE_SITTER_JAVA_REF": ("tree-sitter-java", "https://github.com/tree-sitter/tree-sitter-java"),
        "NEXORA_TREE_SITTER_JSON_REF": ("tree-sitter-json", "https://github.com/tree-sitter/tree-sitter-json"),
        "NEXORA_TREE_SITTER_PYTHON_REF": ("tree-sitter-python", "https://github.com/tree-sitter/tree-sitter-python"),
        "NEXORA_TREE_SITTER_JAVASCRIPT_REF": ("tree-sitter-javascript", "https://github.com/tree-sitter/tree-sitter-javascript"),
        "NEXORA_TREE_SITTER_TYPESCRIPT_REF": ("tree-sitter-typescript", "https://github.com/tree-sitter/tree-sitter-typescript"),
    }
    for variable, (name, url) in mappings.items():
        match = re.search(rf'set\({variable} "([0-9a-f]{{40}})"', cmake)
        if not match:
            continue
        ref = match.group(1)
        add_package(
            packages, relationships, seen,
            name=name,
            version=ref,
            purl=f"pkg:github/{url.split('github.com/', 1)[1]}@{ref}",
            download=f"{url}/archive/{ref}.tar.gz",
        )


def release_files():
    files = []
    dist = ROOT / "dist"
    if not dist.is_dir():
        return files
    for path in sorted(dist.iterdir()):
        if not path.is_file() or path.name.endswith(".spdx.json"):
            continue
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        files.append({
            "SPDXID": spdx_id("File-" + path.name),
            "fileName": "./dist/" + path.name,
            "checksums": [{"algorithm": "SHA256", "checksumValue": digest}],
            "licenseConcluded": "NOASSERTION",
            "copyrightText": "NOASSERTION",
        })
    return files


def main():
    version = sys.argv[1] if len(sys.argv) > 1 else os.environ.get("GITHUB_REF_NAME", "unknown").lstrip("v")
    output = Path(sys.argv[2]) if len(sys.argv) > 2 else ROOT / "dist/NexoraGit-SBOM.spdx.json"
    packages = [{
        "SPDXID": "SPDXRef-Package-NexoraGit",
        "name": "Nexora Git",
        "versionInfo": version,
        "downloadLocation": "https://github.com/Gh0stDeveloper/Nexora-Git",
        "filesAnalyzed": False,
        "licenseConcluded": "Apache-2.0",
        "licenseDeclared": "Apache-2.0",
        "copyrightText": "NOASSERTION",
        "supplier": "Person: Ghost Developer",
    }]
    relationships = [{
        "spdxElementId": "SPDXRef-DOCUMENT",
        "relationshipType": "DESCRIBES",
        "relatedSpdxElement": "SPDXRef-Package-NexoraGit",
    }]
    seen = set()
    gradle_packages(packages, relationships, seen)
    npm_packages(packages, relationships, seen)
    go_packages(packages, relationships, seen)
    native_packages(packages, relationships, seen)

    namespace_seed = os.environ.get("GITHUB_SHA", str(time.time_ns()))
    document = {
        "spdxVersion": "SPDX-2.3",
        "dataLicense": "CC0-1.0",
        "SPDXID": "SPDXRef-DOCUMENT",
        "name": f"NexoraGit-{version}",
        "documentNamespace": f"https://github.com/Gh0stDeveloper/Nexora-Git/sbom/{namespace_seed}",
        "creationInfo": {
            "created": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
            "creators": ["Tool: Nexora Git release-sbom"],
        },
        "packages": packages,
        "files": release_files(),
        "relationships": relationships,
    }

    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(document, indent=2, sort_keys=True) + "\n")
    print(f"Wrote {output} with {len(packages)} packages and {len(document['files'])} release files.")


if __name__ == "__main__":
    main()
