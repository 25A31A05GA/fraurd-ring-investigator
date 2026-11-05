# Build and run the engine with only a JDK. No Maven, no downloads.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY src ./src
RUN mkdir out && javac -d out $(find src/main -name '*.java')

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/out ./out
ENV PORT=8080
EXPOSE 8080
# Default: live REST API on $PORT.  Other modes:  docker run IMAGE demo | bench | farm
ENTRYPOINT ["java", "-cp", "out", "com.frauddetect.Main"]
CMD ["serve"]
