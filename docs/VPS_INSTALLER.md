# Nexora Git VPS Installer

Nexora Git ships a production-oriented VPS installer for the OAuth Auth Broker.

## Supported systems

- Ubuntu Server / Debian
- root or sudo
- a domain whose DNS points to the VPS
- inbound TCP 80 and 443 available to Nginx

The installer is conservative on shared VPS hosts. Existing Nginx sites are preserved, and it never kills an unknown process to claim ports 80/443.

## Architecture

    Internet
       │
       ├─ :80  ─┐
       └─ :443 ─┴─ Nginx (shared by every site on the VPS)
                        │
                        └─ auth.example.com
                               │
                               └─ 127.0.0.1:<auto-selected-port>
                                      │
                                      └─ Nexora Git Auth Broker container

The local broker port is selected from 18080-18180. Only loopback is published. Other web applications may continue sharing Nginx on ports 80/443 through separate server_name virtual hosts.

## Recommended installation

Inspect the bootstrap script first, then run:

    curl -fsSL https://raw.githubusercontent.com/Gh0stDeveloper/Nexora-Git/main/scripts/vps/bootstrap.sh -o /tmp/nexora-git-bootstrap.sh
    less /tmp/nexora-git-bootstrap.sh
    sudo bash /tmp/nexora-git-bootstrap.sh

Or clone manually:

    git clone https://github.com/Gh0stDeveloper/Nexora-Git.git
    cd Nexora-Git
    sudo bash scripts/vps/install.sh

The installer clones/keeps the managed copy in /opt/nexora-git and asks for the Auth Broker domain, Let's Encrypt email, GitHub App Client ID and hidden GitHub App Client Secret.

The GitHub App callback must be https://YOUR_DOMAIN/oauth/callback.

## Dependency behavior

Git, curl/CA certificates, Nginx, Certbot, Docker, Docker Compose and networking/TLS utilities are installed only when missing. apt-get update is skipped when all required packages are already installed.

## Nginx coexistence

The installer creates only the Nexora Git vhost under /etc/nginx/sites-available/nexora-git-auth.conf and its enabled symlink. It checks for duplicate server_name ownership and aborts instead of overwriting another site.

If Nginx is absent but 80/443 are already occupied by another server, installation stops and reports the listener rather than terminating it.

## HTTPS

Certbot obtains a Let's Encrypt certificate and configures HTTPS redirects. Existing certificates for the same domain are reused. The distro renewal timer is enabled when present.

## Secrets

The confidential GitHub App secret remains only in /opt/nexora-git/auth-broker/.env. The file is ignored by Git and restricted to root. Never put the GitHub App Client Secret into the Android APK.

## Management command

    nexora-git status
    nexora-git update
    nexora-git doctor
    nexora-git logs
    nexora-git restart
    nexora-git stop
    nexora-git start
    nexora-git renew-cert
    nexora-git reconfigure
    nexora-git config
    nexora-git version

The config command intentionally hides confidential values.

## Updates

The update command does not reinstall Nginx, Docker, Certbot or operating-system packages. It fetches only the configured branch, requires a fast-forward update, rebuilds using Docker cache, restarts only the broker and validates local/public health.

If the new broker fails to build or fails local health, the updater resets the managed repository to the previous commit and rebuilds that known revision automatically.

## Doctor

The doctor command checks required commands, Docker, Nginx syntax, local broker health, public HTTPS health, TLS certificate lifetime and container state.

## Firewall

The installer never enables UFW. If UFW is already active, it allows the standard Nginx Full profile. Cloud-provider firewalls/security groups must also permit TCP 80 and 443.

## State

Non-secret deployment state is stored in /etc/nexora-git-vps.conf. Broker credentials remain separate under the managed repository and are excluded from Git.
