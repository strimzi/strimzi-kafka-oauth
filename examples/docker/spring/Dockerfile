FROM registry.access.redhat.com/ubi8/openjdk-17

ENTRYPOINT ["java", "-jar", "/usr/share/oauth/server.jar"]

ADD target/*.jar /usr/share/oauth/server.jar