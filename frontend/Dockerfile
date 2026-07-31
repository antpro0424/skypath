# syntax=docker/dockerfile:1

# ---------------------------------------------------------------- dependencies
FROM node:20-alpine AS deps
WORKDIR /app

# npm ci installs exactly what package-lock.json pins, so the image matches what
# was tested rather than whatever satisfies the ranges today.
COPY package.json package-lock.json ./
RUN npm ci

# ---------------------------------------------------------------- build
FROM node:20-alpine AS build
WORKDIR /app
ENV NEXT_TELEMETRY_DISABLED=1

COPY --from=deps /app/node_modules ./node_modules
COPY . .
RUN npm run build

# ---------------------------------------------------------------- runtime
FROM node:20-alpine AS runtime
WORKDIR /app
ENV NODE_ENV=production \
    NEXT_TELEMETRY_DISABLED=1 \
    PORT=3000 \
    HOSTNAME=0.0.0.0

# The standalone bundle carries only the server and the modules it actually uses,
# so devDependencies never reach the runtime image.
COPY --from=build /app/.next/standalone ./
COPY --from=build /app/.next/static ./.next/static

# node:alpine ships an unprivileged "node" user; nothing here needs root.
USER node

EXPOSE 3000
CMD ["node", "server.js"]
