FROM docker.target.com/tap/alpine-openjdk11-build:latest
ENV JAVA_HEAP_PERCENT="70"
ADD favorites-consumer-app/build/distributions/favorites-consumer-app.tar /
ENTRYPOINT ["/favorites-consumer-app/bin/favorites-consumer-app"]
