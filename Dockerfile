# =============================================================================
# Stage 1 — Build: compilar y empaquetar el JAR con Gradle
# =============================================================================
FROM gradle:8.8-jdk21-alpine AS builder

WORKDIR /build

# Copiar solo los archivos de build primero para aprovechar la cache de capas:
# si build.gradle.kts no cambia, Gradle no re-descarga dependencias.
COPY build.gradle.kts settings.gradle.kts ./
RUN gradle dependencies --no-daemon -q

# Copiar el código fuente y compilar
COPY src ./src
RUN gradle bootJar --no-daemon -q

# =============================================================================
# Stage 2 — Runtime: imagen mínima solo con el JRE
# =============================================================================
FROM eclipse-temurin:21-jre-alpine AS runtime

# Crear usuario no-root para ejecutar la aplicación
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# Copiar el JAR desde el stage de build
COPY --from=builder /build/build/libs/payment-reconciliation-*.jar app.jar

# Cambiar propietario al usuario de la app
RUN chown appuser:appgroup app.jar

USER appuser

# Puerto expuesto (debe coincidir con server.port en application.yml)
EXPOSE 8080

# Health check: espera hasta 2 minutos a que el app arranque (Flyway tarda un poco)
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
  CMD wget -qO- http://localhost:8080/api/actuator/health || exit 1

# Opciones JVM optimizadas para contenedor:
# -XX:+UseContainerSupport  → respeta los límites de CPU/RAM del contenedor
# -XX:MaxRAMPercentage=75   → usa como máximo el 75% de la RAM asignada al contenedor
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
