FROM docker.target.com/tap/alpine-openjdk11-build:latest
ENV JAVA_HEAP_PERCENT="70"
RUN wget -O /usr/local/bin/dumb-init https://binrepo.target.com/artifactory/cpe-yum-source/up-agent-tools/dumb-init_1.2.2_amd64 && \
	chmod u+x /usr/local/bin/dumb-init
ADD shopping-list-app/build/distributions/shopping-list-app.tar /
ENTRYPOINT ["/usr/local/bin/dumb-init", "--", "/shopping-list-app/bin/shopping-list-app"]
