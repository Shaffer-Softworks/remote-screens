# The add-on image. Built from addon/ after tools/package_addon.sh has put the server, the editor and the
# player app in addon/dist/ (CI does both; see .github/workflows/addon.yml).
FROM eclipse-temurin:21-jre-alpine

# Java2D draws the maps and needs a font.
RUN apk add --no-cache fontconfig ttf-dejavu

WORKDIR /app
COPY dist/remote-screens-server.jar /app/remote-screens-server.jar
COPY dist/static /app/static
COPY dist/apk /app/apk

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=40 -XX:+UseSerialGC -Djava.awt.headless=true"
EXPOSE 8099 8100
CMD ["java", "-jar", "/app/remote-screens-server.jar"]

ARG BUILD_ARCH
ARG BUILD_VERSION
LABEL \
    io.hass.name="Remote Screens" \
    io.hass.type="addon" \
    io.hass.arch="${BUILD_ARCH}" \
    io.hass.version="${BUILD_VERSION}" \
    org.opencontainers.image.source="https://github.com/Shaffer-Softworks/remote-screens" \
    org.opencontainers.image.licenses="MIT"
