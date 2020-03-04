FROM docker.target.com/tap/alpine-openjdk11-build:latest
ENV JAVA_HEAP_PERCENT="70"
ADD shopping-list-consumer-app/build/distributions/shopping-list-consumer-app.tar /
ENTRYPOINT ["/shopping-list-consumer-app/bin/shopping-list-consumer-app"]
