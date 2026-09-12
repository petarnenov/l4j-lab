# syntax=docker/dockerfile:1
# R-005: the frontend image is built by the project's own `npm run build` and served as static files.
# Build it from this directory:
#   docker build frontend

# Stage 1: build. Node matches frontend/.nvmrc and exists only in this stage.
FROM node:24-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

# Stage 2: runtime. The build output is static files, so a small web server serves it. nginx rather
# than `vite preview`, which Vite documents as unsuitable for serving, and the same server the load
# balancer uses, so the stack has one configuration language.
FROM nginx:1.28-alpine AS runtime
COPY --from=build /app/dist/ /usr/share/nginx/html/
COPY nginx.conf /etc/nginx/conf.d/default.conf
# The stock image's nginx.conf writes its pid file to /var/run, which the unprivileged user cannot.
RUN sed -i 's#^pid .*#pid /tmp/nginx.pid;#' /etc/nginx/nginx.conf \
    && chown -R nginx:nginx /var/cache/nginx /usr/share/nginx/html
USER nginx
EXPOSE 8080
