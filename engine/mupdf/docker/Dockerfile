# Simple MuPDF Android build - easy to remove when done
FROM ubuntu:22.04

ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update && apt-get install -y --no-install-recommends \
    openjdk-17-jdk-headless \
    git \
    make \
    python3 \
    unzip \
    wget \
    file \
    binutils \
    && rm -rf /var/lib/apt/lists/*

# Android SDK + NDK (minimal install)
ENV ANDROID_HOME=/opt/android-sdk
# NDK path after sdkmanager install (version in sdkmanager command below)
ARG NDK_VER=26.2.11394342
ENV ANDROID_NDK_HOME=${ANDROID_HOME}/ndk/${NDK_VER}
ENV PATH=${PATH}:${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_NDK_HOME}

RUN mkdir -p ${ANDROID_HOME}/cmdline-tools && cd ${ANDROID_HOME}/cmdline-tools \
    && wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip \
    && unzip -q commandlinetools-linux-11076708_latest.zip \
    && mv cmdline-tools latest \
    && rm commandlinetools-linux-11076708_latest.zip

RUN yes | sdkmanager --sdk_root=${ANDROID_HOME} --licenses 2>/dev/null || true \
    && sdkmanager --sdk_root=${ANDROID_HOME} "ndk;${NDK_VER}" "platform-tools"

WORKDIR /build
COPY build.sh /build/build.sh
RUN chmod +x /build/build.sh

ENTRYPOINT ["/build/build.sh"]
