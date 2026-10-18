# syntax=docker/dockerfile:1
# Imagen única para los 4 microservicios. Se elige cuál construir con el argumento MODULE:
#   docker build --build-arg MODULE=pedidos-service -t pedidos-service .

FROM maven:3.9-eclipse-temurin-21 AS build
ARG MODULE
WORKDIR /build
COPY . .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -pl ${MODULE} -am package -DskipTests

FROM eclipse-temurin:21-jre-alpine
ARG MODULE
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /build/${MODULE}/target/${MODULE}.jar app.jar
USER app
ENV JAVA_OPTS="-Xms128m -Xmx384m"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
