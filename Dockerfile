# CI에서 만든 bootJar를 실행 전용 이미지에 담는다. 빌드는 서버가 아니라 GitHub Actions에서 한다.
FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY build/libs/jipdon-api-*-SNAPSHOT.jar app.jar
USER app

# 게이트웨이와 같은 2GB 서버를 나눠 쓴다: 힙 256MB, 컨테이너 한도 448MB (deploy/compose.prod.yaml)
ENV JAVA_TOOL_OPTIONS="-Xmx256m -XX:MaxMetaspaceSize=128m -XX:+ExitOnOutOfMemoryError"
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "app.jar"]
