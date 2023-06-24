FROM gradle:jdk19 as builder
WORKDIR /usr/app
COPY . .
RUN gradle --no-daemon :plugin:installBot

FROM ibm-semeru-runtimes:open-19-jre-focal

LABEL org.opencontainers.image.source = "https://github.com/DRSchlaubi/mikbot"

WORKDIR /usr/app
COPY --from=builder /usr/app/plugin/build/installVoteBot .

ENTRYPOINT ["/usr/app/bin/mikmusic"]
