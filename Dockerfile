FROM openjdk:8-jdk-alpine

WORKDIR /app

ADD yxinmiracle-oj-code-sandbox-0.0.1-SNAPSHOT.jar .

EXPOSE 8667

ENTRYPOINT ["java", "-Xmx512m","-jar","/app/yxinmiracle-oj-code-sandbox-0.0.1-SNAPSHOT.jar"]