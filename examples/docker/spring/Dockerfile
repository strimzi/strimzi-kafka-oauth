FROM adoptopenjdk/openjdk11:ubi-jre

ENTRYPOINT ["java", "-jar", "/usr/share/oauth/server.jar"]

ADD target/*.jar /usr/share/oauth/server.jar