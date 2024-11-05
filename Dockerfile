FROM --platform=$TARGETOS/$TARGETARCH eclipse-temurin:23-jre-alpine

WORKDIR /usr/app
COPY plugin/build/install/bot-plugin .

ENTRYPOINT ["/usr/app/bin/mikmusic"]
