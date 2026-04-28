# -----------------------------
# Backend build stage
# -----------------------------
FROM --platform=$BUILDPLATFORM maven:3.9.11-eclipse-temurin-17 AS backend-builder
WORKDIR /build

COPY pom.xml mvnw mvnw.cmd ./
COPY .mvn ./.mvn
COPY src ./src

RUN chmod +x mvnw && ./mvnw clean package -DskipTests

# -----------------------------
# Frontend build stage
# -----------------------------
FROM --platform=$BUILDPLATFORM node:20-alpine AS frontend-builder
WORKDIR /build/webui

COPY webui/package*.json ./
RUN npm ci

COPY webui .
RUN npm run build

# -----------------------------
# Runtime stage (JDK + Nginx)
# -----------------------------
FROM eclipse-temurin:17-jre

RUN set -eux; \
    if command -v apk >/dev/null 2>&1; then \
      apk add --no-cache nginx; \
    elif command -v apt-get >/dev/null 2>&1; then \
      apt-get update; \
      apt-get install -y --no-install-recommends nginx; \
      rm -rf /var/lib/apt/lists/*; \
    elif command -v microdnf >/dev/null 2>&1; then \
      microdnf install -y nginx; \
      microdnf clean all; \
      rm -rf /var/cache/dnf; \
    elif command -v dnf >/dev/null 2>&1; then \
      dnf install -y nginx; \
      dnf clean all; \
      rm -rf /var/cache/dnf; \
    elif command -v yum >/dev/null 2>&1; then \
      yum install -y nginx; \
      yum clean all; \
      rm -rf /var/cache/yum; \
    else \
      echo "No supported package manager found to install nginx"; \
      exit 1; \
    fi

WORKDIR /app

COPY --from=backend-builder /build/target/*.jar /app/app.jar
COPY --from=frontend-builder /build/webui/dist /usr/share/nginx/html
COPY nginx/default.conf /etc/nginx/conf.d/default.conf
COPY docker-entrypoint.sh /app/docker-entrypoint.sh

RUN sed -i '\|include /etc/nginx/sites-enabled/\*;|d' /etc/nginx/nginx.conf
RUN chmod +x /app/docker-entrypoint.sh \
    && mkdir -p /run/nginx

EXPOSE 80

ENTRYPOINT ["/app/docker-entrypoint.sh"]
