# syntax=docker/dockerfile:1.7
# =============================================================================
# Dockerfile multi-stage (clase 2.1.1: "Docker Multi Stage" + "Optimizando cache")
#
#   dependencies -> descarga y cachea las dependencias de Maven
#   test         -> ejecuta las pruebas unitarias (si fallan, la imagen NO se construye)
#   compile      -> empaqueta el .jar (depende de que "test" haya pasado)
#   prod         -> imagen final mínima: solo JRE + jar, usuario sin privilegios
# =============================================================================

# -----------------------------------------------------------------------------
# Etapa 1: dependencias (se reutiliza mientras no cambie el pom.xml)
# -----------------------------------------------------------------------------
FROM eclipse-temurin:24-jdk AS dependencies
WORKDIR /app
COPY pom.xml .
COPY mvnw .
COPY .mvn .mvn
# chmod: en Windows se pierde el permiso de ejecución del script
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B -q

# -----------------------------------------------------------------------------
# Etapa 2: pruebas unitarias
# -----------------------------------------------------------------------------
FROM eclipse-temurin:24-jdk AS test
WORKDIR /app
# Reutiliza el caché de dependencias de la etapa anterior
COPY --from=dependencies /root/.m2 /root/.m2
COPY --from=dependencies /app /app
COPY src ./src
RUN ./mvnw test -B -Djacoco.skip=true

# -----------------------------------------------------------------------------
# Etapa 3: compilación (al copiar /app desde "test" se crea la dependencia
# entre etapas: si las pruebas fallan, esta etapa nunca se ejecuta)
# -----------------------------------------------------------------------------
FROM eclipse-temurin:24-jdk AS compile
WORKDIR /app
COPY --from=dependencies /root/.m2 /root/.m2
COPY --from=test /app /app
RUN ./mvnw clean package -DskipTests -Djacoco.skip=true -B -q \
 && java -Djarmode=tools -jar target/ms-inventario.jar extract --layers --destination extracted \
 && mkdir -p extracted/dependencies extracted/spring-boot-loader \
             extracted/snapshot-dependencies extracted/application
# El jar se separa en capas (librerías / aplicación): si solo cambia el código,
# solo se reemplaza la capa pequeña y las descargas/despliegues son más rápidos.

# -----------------------------------------------------------------------------
# Etapa 4: producción. Solo JRE (no JDK ni Maven) sobre Alpine -> imagen pequeña
# y con menos superficie de ataque.
# -----------------------------------------------------------------------------
FROM eclipse-temurin:24-jre-alpine AS prod

ARG GIT_COMMIT=local
ARG PIPELINE_RUN=local
ARG BUILD_DATE=unknown

# Metadatos OCI: permiten saber desde la imagen qué commit la generó (trazabilidad)
LABEL org.opencontainers.image.title="ms-inventario" \
      org.opencontainers.image.description="Microservicio de inventario - DOY0101" \
      org.opencontainers.image.revision="${GIT_COMMIT}" \
      org.opencontainers.image.created="${BUILD_DATE}"

ENV GIT_COMMIT=${GIT_COMMIT} \
    PIPELINE_RUN=${PIPELINE_RUN} \
    TZ=America/Santiago

# Usuario sin privilegios (nunca correr la aplicación como root)
RUN addgroup -S -g 10001 app && adduser -S -u 10001 -G app -h /app app

WORKDIR /app
COPY --from=compile --chown=app:app /app/extracted/dependencies/ ./
COPY --from=compile --chown=app:app /app/extracted/spring-boot-loader/ ./
COPY --from=compile --chown=app:app /app/extracted/snapshot-dependencies/ ./
COPY --from=compile --chown=app:app /app/extracted/application/ ./

USER 10001:10001
EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8080/actuator/health/liveness || exit 1

# MaxRAMPercentage: la JVM respeta el límite de memoria del contenedor.
# ExitOnOutOfMemoryError: si se queda sin memoria, el orquestador reinicia el contenedor.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:+ExitOnOutOfMemoryError", "-jar", "ms-inventario.jar"]
