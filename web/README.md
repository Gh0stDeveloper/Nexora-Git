# Nexora Git download website

Official self-hosted download and project website for Nexora Git.

## Stack

- Next.js 16.3.8 App Router
- React 19.3
- TypeScript
- Tailwind CSS 4.3
- Node.js 24 LTS in the production container

The production VPS serves the website on the same domain as the Auth Broker. Nginx keeps the broker routes isolated and forwards all normal website traffic to the web container.

The website reads release metadata from `NEXORA_WEB_RELEASE_METADATA`. A signed APK is published by the VPS signing workflow; the site never receives the signing key.

## Development

```bash
npm install
npm run typecheck
npm run dev
```

## Production build

```bash
npm run typecheck
npm run build
```

The Next.js build uses `output: "standalone"` for a minimal production runtime.
