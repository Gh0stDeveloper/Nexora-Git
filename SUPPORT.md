# Support

Nexora Git is an open-source Android Git and GitHub workspace. This document separates usage help, bug reports and security reports so that sensitive or operational issues reach the correct channel.

## Usage help

Before opening an issue:

1. Check the [README](README.md) and [documentation hub](docs/README.md).
2. Review the relevant workflow document, especially authentication, Git, storage, VPS or release guidance.
3. Search existing issues for the same symptom.
4. Verify the problem against the latest supported beta/RC or stable build when one exists.

When GitHub Discussions is enabled, general questions and ideas should use Discussions rather than the bug tracker.

## Bug reports

Use the repository bug-report form for reproducible product defects. Include:

- Nexora Git version;
- Android version and device;
- affected area;
- exact reproduction steps;
- expected and actual behavior;
- sanitized logs when useful.

Never attach tokens, private repository content, signing material, production environment files or unredacted credentials.

## Security reports

Do not report vulnerabilities through a public issue or Discussion. Follow [SECURITY.md](SECURITY.md) and use GitHub private vulnerability reporting when it is available.

## Self-hosted deployment

For Auth Broker/VPS issues, include only non-secret topology details and sanitized command output. The operator remains responsible for DNS, TLS, firewall rules, GitHub App configuration and production credentials.
