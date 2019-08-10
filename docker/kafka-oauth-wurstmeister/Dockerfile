FROM wurstmeister/kafka:latest

COPY libs/* /opt/kafka/libs/
COPY start.sh /opt/kafka/

CMD ["/bin/bash", "/opt/kafka/start.sh"]