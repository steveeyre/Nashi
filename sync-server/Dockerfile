FROM eclipse-temurin:17-jdk AS build
WORKDIR /build
COPY . .
RUN ./gradlew :sync-server:installDist --no-daemon -x test || gradle installDist --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /build/sync-server/build/install/sync-server/ ./
EXPOSE 8080
ENTRYPOINT ["bin/sync-server"]
