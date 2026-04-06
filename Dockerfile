FROM eclipse-temurin:17-jdk-jammy AS nanosdk-builder

WORKDIR /tmp

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
       build-essential \
       ca-certificates \
       cmake \
       curl \
       git \
       libmbedtls-dev \
       libssl-dev \
       ninja-build \
       pkg-config \
       python3 \
    && rm -rf /var/lib/apt/lists/*

RUN git clone --depth 1 --recurse-submodules https://github.com/emqx/NanoSDK.git nanosdk

WORKDIR /tmp/nanosdk

RUN cmake -S . -B build -G Ninja \
    -DBUILD_SHARED_LIBS=ON \
    -DNNG_ENABLE_QUIC=ON \
    -DNNG_ENABLE_TLS=ON \
    -DNNG_TESTS=OFF \
    -DNNG_TOOLS=OFF \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_INSTALL_PREFIX=/opt/nanosdk

RUN mkdir -p /opt/nanosdk/lib \
    && cmake --build build --parallel --target nng \
    && find build -name 'libnng.so*' -exec cp -a {} /opt/nanosdk/lib/ \; \
    && find build -name 'libmsquic.so*' -exec cp -a {} /opt/nanosdk/lib/ \;

FROM node:20-bookworm-slim AS frontend-builder

WORKDIR /app/frontend

COPY frontend/package*.json ./
RUN npm ci

COPY frontend/ ./
RUN npm run build

FROM eclipse-temurin:17-jdk-jammy AS backend-builder

WORKDIR /app/backend

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
       ca-certificates \
       maven \
    && rm -rf /var/lib/apt/lists/*

COPY backend/pom.xml ./pom.xml
RUN mvn dependency:go-offline -B

COPY backend/src ./src
RUN mvn clean package -DskipTests -B

FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
       ca-certificates \
       libmbedcrypto7 \
       libmbedtls14 \
       libmbedx509-1 \
       tzdata \
    && rm -rf /var/lib/apt/lists/*

COPY --from=nanosdk-builder /opt/nanosdk /opt/nanosdk
COPY --from=backend-builder /app/backend/target/*.jar /app/app.jar
COPY --from=frontend-builder /app/frontend/dist /opt/static

ENV TZ=Asia/Shanghai
ENV LD_LIBRARY_PATH=/opt/nanosdk/lib
ENV LD_PRELOAD=/opt/nanosdk/lib/libmsquic.so
ENV JNA_LIBRARY_PATH=/opt/nanosdk/lib

EXPOSE 8080

ENTRYPOINT ["java", "-Dspring.web.resources.static-locations=file:/opt/static/", "-jar", "/app/app.jar"]
