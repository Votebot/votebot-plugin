FROM gradle:jdk19 as builder
WORKDIR /usr/app
COPY . .
RUN ./gradlew --no-daemon plugin:installBotArchive

FROM eclipse-temurin:19-jre-alpine

WORKDIR /usr/app
COPY --from=builder /usr/app/plugin/build/installBot .

ENTRYPOINT ["/usr/app/bin/mikmusic"]
