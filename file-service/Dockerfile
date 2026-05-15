# syntax=docker/dockerfile:1.7

FROM maven:3.9-eclipse-temurin-17 AS build
ARG MAVEN_MIRROR_URL=https://repo.maven.apache.org/maven2
ARG MAVEN_BUILD_RETRIES=4
ENV MAVEN_CONFIG=/root/.m2
ENV MAVEN_MIRROR_URL=${MAVEN_MIRROR_URL}
ENV MAVEN_OPTS="-Djava.net.preferIPv4Stack=true -Djava.net.preferIPv6Addresses=false -Dmaven.wagon.http.retryHandler.count=3"
WORKDIR /workspace
COPY settings.xml /root/.m2/settings.xml
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2/repository \
    bash -ec 'for i in $(seq 1 "$MAVEN_BUILD_RETRIES"); do mvn -B -ntp -DskipTests dependency:go-offline && exit 0; echo "dependency:go-offline failed on attempt $i/$MAVEN_BUILD_RETRIES"; sleep $((i * 5)); done; exit 1'
COPY src ./src
RUN --mount=type=cache,target=/root/.m2/repository \
    bash -ec 'for i in $(seq 1 "$MAVEN_BUILD_RETRIES"); do mvn -B -ntp -DskipTests package && exit 0; echo "package failed on attempt $i/$MAVEN_BUILD_RETRIES"; sleep $((i * 5)); done; exit 1'

FROM eclipse-temurin:17-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /workspace/target/*.jar app.jar
EXPOSE 8083
ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
