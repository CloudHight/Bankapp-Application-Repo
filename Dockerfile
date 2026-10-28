# syntax=docker/dockerfile:1
#
# Runtime image. The Jenkins pipeline builds the JAR with Maven first
# (mvn package), so this stage only needs a JRE + the artifact.
# Build:  docker build --build-arg JAR_FILE=target/bankapp.jar -t bankapp .
FROM eclipse-temurin:17-jre-jammy

ARG JAR_FILE=target/bankapp.jar

ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0"

WORKDIR /app

# curl is used by the container HEALTHCHECK; create an unprivileged user to run as.
# archive.ubuntu.com's public mirror pool is flaky/slow from EC2 (multi-minute
# stalls on individual index files); the eu-west-3 EC2-regional mirror is the
# same one stock Ubuntu AMIs use and stays fast. security.ubuntu.com has no
# per-region mirror, so it's left as-is; Retries/Timeout keep a bad pick from
# stalling for minutes instead of failing fast.
RUN sed -i 's|http://archive\.ubuntu\.com/ubuntu|http://eu-west-3.ec2.archive.ubuntu.com/ubuntu|g' /etc/apt/sources.list \
    && apt-get update -o Acquire::Retries=3 -o Acquire::http::Timeout=15 \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system bankapp \
    && useradd --system --gid bankapp --home /app bankapp

COPY ${JAR_FILE} /app/bankapp.jar
RUN chown -R bankapp:bankapp /app
USER bankapp

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
    CMD curl -fsS http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/bankapp.jar"]
