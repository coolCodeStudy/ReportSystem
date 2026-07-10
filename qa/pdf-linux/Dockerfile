FROM ubuntu:24.04

RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
        libreoffice-writer \
        fonts-noto-cjk \
        fonts-noto-color-emoji \
        poppler-utils \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /work

