FROM openjdk:8-jre

ENTRYPOINT ["java", "-jar", "/usr/share/oauth/server.jar"]

ARG JAR_FILE
ADD target/${JAR_FILE} /usr/share/oauth/server.jar