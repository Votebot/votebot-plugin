FROM eclipse-temurin:22-jre-alpine

WORKDIR /usr/app
COPY --from=builder build/install/bot-plugin .

ENTRYPOINT ["/usr/app/bin/mikmusic"]
