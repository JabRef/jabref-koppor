# Reproducible, pinned runner for the search-backend MWEs (ADR 0064 / issue #12708).
# It exists so the numbers do not depend on a particular dev laptop, and so the
# resource-constrained and arm64 claims can actually be exercised:
#
#   docker build -t jabref-search-mwe .
#   docker run --rm -v "$PWD/out:/mwe/out" jabref-search-mwe            # full run
#   docker run --rm -v "$PWD/out:/mwe/out" jabref-search-mwe 10000      # smaller dataset
#   docker run --rm --cpus=2 --memory=2g -v "$PWD/out:/mwe/out" jabref-search-mwe   # low-end machine
#   docker buildx build --platform linux/arm64 -t jabref-search-mwe:arm64 .         # linux-arm64
#
# Cross-platform for macOS/Windows is a GitHub Actions matrix job (jbang runs natively there);
# Docker only covers linux/amd64 and linux/arm64.
FROM eclipse-temurin:25-jdk

RUN apt-get update && apt-get install -y --no-install-recommends curl bash unzip \
 && rm -rf /var/lib/apt/lists/*

# jbang manages script deps; the base image already provides JDK 25 to satisfy //JAVA 25.
RUN curl -Ls https://sh.jbang.dev | bash
ENV PATH="/root/.jbang/bin:${PATH}"

WORKDIR /mwe
COPY *.java *.sh ./

# Warm the dependency cache (sqlite-jdbc, …) at build time so `docker run` is offline and reproducible.
RUN jbang build GenData.java SqliteRegex.java || true

ENTRYPOINT ["bash", "run-suite.sh"]
