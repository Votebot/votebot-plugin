FROM gradle:jdk21 as builder
WORKDIR /usr/app
COPY . .
RUN ./gradlew --no-daemon plugin:installBotArchive

FROM eclipse-temurin:21-jre-alpine

WORKDIR /usr/app
COPY --from=builder /usr/app/plugin/build/installBot .

ENTRYPOINT ["/usr/app/bin/mikmusic"]
