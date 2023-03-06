FROM eclipse-temurin:17-ubi9-minimal

ENV APP_DIR /application
ENV APP_FILE container-uber-jar.jar
ENV DEBUG_SUSPEND_FLAG n
ENV JAVA_DEBUG_PORT *:5005

EXPOSE 8090 8091

WORKDIR $APP_DIR
COPY target/*-fat.jar $APP_DIR/$APP_FILE

ENTRYPOINT ["bash", "-c"]
CMD ["JAVA_DEBUG_ARG=$( [[ \"$JAVA_DEBUG\" == \"y\" ]] && echo \"-agentlib:jdwp=transport=dt_socket,server=y,suspend=$DEBUG_SUSPEND_FLAG,address=$JAVA_DEBUG_PORT\" ) ; exec java $JAVA_DEBUG_ARG -jar $APP_FILE"]
