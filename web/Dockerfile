FROM node:24.21.0-bookworm-slim AS deps
WORKDIR /app
COPY package.json .npmrc ./
RUN npm install --no-audit --no-fund

FROM node:24.21.0-bookworm-slim AS builder
WORKDIR /app
ENV NEXT_TELEMETRY_DISABLED=1
COPY --from=deps /app/node_modules ./node_modules
COPY . .
ARG NEXT_PUBLIC_SITE_URL
ENV NEXT_PUBLIC_SITE_URL=${NEXT_PUBLIC_SITE_URL}
RUN npm run typecheck && npm run build

FROM node:24.21.0-bookworm-slim AS runner
WORKDIR /app
ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1
ENV HOSTNAME=0.0.0.0
ENV PORT=3000
RUN groupadd --system --gid 1001 nexora && useradd --system --uid 1001 --gid nexora --home-dir /nonexistent --shell /usr/sbin/nologin nexora
COPY --from=builder --chown=nexora:nexora /app/.next/standalone ./
COPY --from=builder --chown=nexora:nexora /app/.next/static ./.next/static
COPY --from=builder --chown=nexora:nexora /app/public ./public
USER nexora
EXPOSE 3000
CMD ["node", "server.js"]
