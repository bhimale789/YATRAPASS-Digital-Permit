# ---- build ----
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npm run build

# ---- runtime (unprivileged nginx: HTTP 8080, HTTPS 8443 when a certificate is mounted) ----
FROM nginxinc/nginx-unprivileged:1.27-alpine
COPY nginx/app.conf /etc/nginx/snippets/app.conf
COPY nginx/http.conf /etc/nginx/conf.d/default.conf
COPY nginx/https.conf.template /etc/nginx/https.conf.template
COPY --chmod=755 nginx/30-enable-https.sh /docker-entrypoint.d/30-enable-https.sh
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 8080 8443
