FROM docker.io/library/eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY target/payments-ledger-service-0.1.0.jar app.jar
COPY otel/opentelemetry-javaagent.jar /otel/opentelemetry-javaagent.jar
USER 1000:1000
ENTRYPOINT ["java", "-javaagent:/otel/opentelemetry-javaagent.jar", "-Xmx384m", "-jar", "app.jar"]
