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

# 复制本地编译好的 jar 文件和静态资源
COPY target/*.jar /app/app.jar
COPY src/main/resources/static /opt/static 2>/dev/null || true

ENV TZ=Asia/Shanghai
ENV LD_LIBRARY_PATH=/opt/nanosdk/lib
ENV LD_PRELOAD=/opt/nanosdk/lib/libmsquic.so
ENV JNA_LIBRARY_PATH=/opt/nanosdk/lib

EXPOSE 8080

ENTRYPOINT ["java", "-Dspring.web.resources.static-locations=file:/opt/static/", "-jar", "/app/app.jar"]
