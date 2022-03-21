Metrics example
===============

The following instructions deploy the metrics example into the default Kubernetes namespace.
It is assumed that Strimzi Kafka Operator has already been deployed, and that your current working directory is this directory.

We also assume that you have deployed `keycloak` according to the instructions in [README.md](README.md#deploying-the-postgres-and-keycloak-that-stores-state-to-postgres).


Deploying Kafka cluster
-----------------------

    kubectl create -f kafka-oauth-single-authz-metrics.yaml

    # Check that metrics endpoint is working
    kubectl exec -ti my-cluster-kafka-0 -- /bin/sh
    curl -s http://localhost:9404


Deploying Prometheus
--------------------

Deploy Prometheus operator:

    kubectl create -f https://raw.githubusercontent.com/coreos/prometheus-operator/master/bundle.yaml

    export HTTP_OPERATOR_MAIN=https://raw.githubusercontent.com/strimzi/strimzi-kafka-operator/main

Deploy additional configuration:

    kubectl apply -f $HTTP_OPERATOR_MAIN/examples/metrics/prometheus-additional-properties/prometheus-additional.yaml

If you want to deploy to the `default` namespace rather than `myproject` use the following for the next step:

    curl -s $HTTP_OPERATOR_MAIN/examples/metrics/prometheus-install/strimzi-pod-monitor.yaml | sed -e 's/myproject/default/' | kubectl apply -f -

Otherwise use:

    kubectl apply -f $HTTP_OPERATOR_MAIN/examples/metrics/prometheus-install/strimzi-pod-monitor.yaml

Continue with:

    kubectl apply -f $HTTP_OPERATOR_MAIN/examples/metrics/prometheus-install/prometheus-rules.yaml

    # Deploy scraping configuration for custom clients
    kubectl apply -f prometheus-scrape-custom.yaml

And again you want to deploy to the `default` namespace rather than `myproject` use the following for the next step:

    # Deploy Prometheus instance
    curl -s $HTTP_OPERATOR_MAIN/examples/metrics/prometheus-install/prometheus.yaml | sed -e 's/myproject/default/' | kubectl apply -f -

Otherwise use:

    kubectl apply -f $HTTP_OPERATOR_MAIN/examples/metrics/prometheus-install/prometheus.yaml

Finally expose the port to localhost:

    kubectl port-forward svc/prometheus-operated 9090:9090


Connecting to Prometheus
------------------------

    # Check that metrics are collected by Prometheus
    open http://localhost:9090

    # Query: strimzi_oauth_http_requests_count 


Deploying the Kafka Producer Pod
--------------------------------

    # Create a secret containing a client secret for OAuth authentication
     kubectl create secret generic kafka-client-secret --from-literal=secret=team-a-client-secret

    # Deploy kafka-producer-client
    cat kafka-oauth-authz-metrics-client.yaml | sed -e "s/kafka-client-shell/kafka-producer-client/" | kubectl create -f -

To attach to the container:

    kubectl attach -ti kafka-producer-client
    . /tmp/bin/prepare-env.sh

Manually send each message line by line

    bin/kafka-console-producer.sh --broker-list my-cluster-kafka-bootstrap:9092 --topic a_messages   --producer.config=$HOME/team-a-client.properties

Or better, send a message each second

    for i in {1..1000}; do sleep 1 && echo "Message $i"; done | bin/kafka-console-producer.sh --broker-list my-cluster-kafka-bootstrap:9092 --topic a_messages   --producer.config=$HOME/team-a-client.properties

Connect to prometheus endpoint from any other pod:

    curl http://kafka-producer-client:9404


Deploying the Kafka Consumer Pod
--------------------------------

    # Create a secret containing a client secret for OAuth authentication
    # It may already exist from before in which case just ignore it
     kubectl create secret generic kafka-client-secret --from-literal=secret=team-a-client-secret

    cat kafka-oauth-authz-metrics-client.yaml | sed -e "s/kafka-client-shell/kafka-consumer-client/" | kubectl create -f -

To attach to the container:

    kubectl attach -ti kafka-consumer-client
    . /tmp/bin/prepare-env.sh

Consume messages in real time

    bin/kafka-console-consumer.sh --bootstrap-server my-cluster-kafka-bootstrap:9092 --topic a_messages \
      --from-beginning --consumer.config ~/team-a-client.properties --group a_consumer_group_1

Connect to prometheus endpoint from any other pod:

    curl http://kafka-consumer-client:9404


### Remotely connecting to JMX

We can connect to the JMX remotely to inspect the content. We can use `jmxterm` project for that:

```
git clone https://github.com/jiaqi/jmxterm.git
cd jmxterm
git checkout -b v1.0.2
mvn clean install
```

We assume that your Kafka client / broker is running locally with the following additional command-line options, which starts an unprotected JXM server on port 9500:

```
-Djava.awt.headless=true -Dcom.sun.management.jmxremote -Dcom.sun.management.jmxremote.port=9500 \
   -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false
```

We can then use the `jmxterm` by running:

```
java -jar target/jmxterm-1.0.2-uber.jar
open localhost:9500
domains
beans -d strimzi.oauth
info -b <BEAN NAME>
get -s -b <BEAN NAME> count
```
