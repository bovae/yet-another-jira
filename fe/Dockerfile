FROM node:22 AS build

WORKDIR /app

# Copy manifests first so npm ci is cached until the lockfile changes.
COPY package.json package-lock.json ./
RUN npm ci

# Build the bundle; the build script runs tsc --noEmit, so type errors fail the image.
COPY . .
RUN npm run build

FROM nginx:alpine AS runtime

# SPA history fallback + /api reverse proxy.
COPY nginx.conf /etc/nginx/conf.d/default.conf

COPY --from=build /app/dist /usr/share/nginx/html

# nginx serves on 80.
EXPOSE 80

# nginx:alpine ships an entrypoint/cmd that runs nginx in the foreground.
