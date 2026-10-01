FROM eclipse-temurin:21-jdk

# Build tools + Node.js 22 (required by Claude Code)
RUN apt-get update && apt-get install -y --no-install-recommends \
        curl ca-certificates git maven \
    && curl -fsSL https://deb.nodesource.com/setup_22.x | bash - \
    && apt-get install -y --no-install-recommends nodejs \
    && rm -rf /var/lib/apt/lists/*

RUN npm install -g @anthropic-ai/claude-code

WORKDIR /workspace

ENTRYPOINT ["claude"]