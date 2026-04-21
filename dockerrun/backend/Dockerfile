FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
       ca-certificates \
       iproute2 \
       libmbedcrypto7 \
       libmbedtls14 \
       libmbedx509-1 \
       tzdata \
    && rm -rf /var/lib/apt/lists/*

COPY cross-domain-exchange-1.0.0.jar /app/app.jar

ENV TZ=Asia/Shanghai
ENV LD_LIBRARY_PATH=/opt/nanosdk/lib
ENV LD_PRELOAD=/opt/nanosdk/lib/libmsquic.so
ENV JNA_LIBRARY_PATH=/opt/nanosdk/lib

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
