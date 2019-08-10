FROM confluentinc/cp-kafka:latest

COPY libs/* /usr/share/java/kafka/
COPY start.sh /etc/confluent/docker/
COPY start-run.sh /etc/confluent/docker/

CMD ["/bin/bash", "/etc/confluent/docker/start.sh"]