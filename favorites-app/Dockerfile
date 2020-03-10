FROM docker.target.com/tap/alpine-openjdk11-build:latest
ENV JAVA_HEAP_PERCENT="70"
RUN wget -O /usr/local/bin/dumb-init https://binrepo.target.com/artifactory/cpe-yum-source/up-agent-tools/dumb-init_1.2.2_amd64 && \
	chmod u+x /usr/local/bin/dumb-init
ADD favorites-app/build/distributions/favorites-app.tar /
ENTRYPOINT ["/usr/local/bin/dumb-init", "--", "/favorites-app/bin/favorites-app"]
