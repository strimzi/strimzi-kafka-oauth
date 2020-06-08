Examples of Strimzi Kafka Cluster with OAuth
--------------------------------------------

Here are several examples of Kafka Cluster definitions for deployment with Strimzi Cluster Operator.
They assume Keycloak is used as an authorization server, with properly configured realms called 'demo', and 'authz'.

* `keycloak.yaml`

  A Keycloak pod you can use to start an ephemeral instance of Keycloak. Any changes to realms will be lost when the pod shuts down. This is the first yaml you'll want to deploy.

* `kafka-oauth-singe.yaml`

  A single node Kafka cluster using Apache Kafka 2.3.1 with OAuth 2 authentication using the 'demo' realm, and fast local signature validation (with keys loaded from the JWKS endpoint) for validating access tokens.

* `kafka-oauth-single-2_4.yaml`

  Same as `kafka-oauth-single.yaml` except using Apache Kafka 2.4.0.

* `kafka-oauth-single-introspect.yaml`

  A single node Kafka cluster using Apache Kafka 2.3.1 with OAuth 2 authentication using the `demo` realm, and introspection endpoint for access token validation.

* `kafka-oauth-single-authz.yaml`

  A single node Kafka cluster using Apache Kafka 2.3.1 with OAuth 2 authentication using the `kafka-authz` realm, a fast local signature validation, and Keycloak Authorization Services for token-based authorization.

* `kafka-oauth-single-2_4.authz.yaml`

  Same as `kafka-oauth-single-authz.yaml` except using Apache Kafka 2.4.0.

### Deploying Keycloak and accessing the Keycloak Admin Console

Before deploying any of the Kafka cluster definitions, you need to deploy a Keycloak instance, and configure the realms with the necessary client definitions.

#### Deploying the ephemeral Keycloak instance

Deploy the simple Keycloak server with an in-memory database that does not survive container restart:

    kubectl apply -f keycloak.yaml 

Wait for Keycloak to start up:

    kubectl get pod
    kubectl logs $(kubectl get pod | grep keycloak | awk '{print $1}') -f

In order to connect to Keycloak Admin Console you need an ip address and a port where it is listening. From the point of view of the Keycloak pod it is listening on port 8080 on all the interfaces. The `NodePort` service also exposes a port on the Kubernetes Node's IP:

    kubectl get svc | grep keycloak
    KEYCLOAK_PORT=$(kubectl get svc | grep keycloak | awk -F '8080:' '{print $2}' | awk -F '/' '{print $1}')
    echo Keycloak port: $KEYCLOAK_PORT 

The actual IP address and port to use in order to reach Keycloak Admin Console from your host machine depends on your Kubernetes installation.


#### Deploying the Postgres and Keycloak that stores state to Postgres

First, we need a stable filesystem that is remounted if the Postgres pod is deleted, and recreated:

    kubectl apply -f postgres-pvc.yaml
    
Then, we need to start Postgres:

    kubectl apply -f postgres.yaml

And finally, start a Keycloak pod that uses Postgres:

    kubectl apply -f keycloak-postgres.yaml
 

#### Minishift

    KEYCLOAK_HOST=$(minishift ip)
    KEYCLOAK_PORT=$(kubectl get svc | grep keycloak | awk -F '8080:' '{print $2}' | awk -F '/' '{print $1}')
    echo http://$KEYCLOAK_HOST:$KEYCLOAK_PORT/auth/admin

You can then open the printed URL and login with admin:admin.


#### Minikube

You can connect directly to Kubernetes Node IP using a NodePort port:

    KEYCLOAK_HOST=$(minikube ip)
    KEYCLOAK_PORT=$(kubectl get svc | grep keycloak | awk -F '8080:' '{print $2}' | awk -F '/' '{print $1}')
    echo http://$KEYCLOAK_HOST:$KEYCLOAK_PORT/auth/admin

You can then open the printed URL and login with admin:admin.


#### Kubernetes Kind

In order to connect to Keycloak Admin Console you have to create a TCP tunnel:

    kubectl port-forward svc/keycloak 8080:8080
    
You can then open: http://localhost:8080/auth/admin and login with admin:admin.    


### Importing example realms

This step depends on your development environment because we have to build a custom docker image, and deploy it as a Kubernetes pod, for which we have to push it to the Docker Registry first.

First we build the `keycloak-import` docker image:

    cd ../docker/keycloak-import
    docker build . -t strimzi/keycloak-import

Then we tag and push it to the Docker Registry:

    docker tag strimzi/keycloak-import $REGISTRY_IP:$REGISTRY_PORT/strimzi/keycloak-import
    docker push $REGISTRY_IP:$REGISTRY_PORT/strimzi/keycloak-import

Here we assume we know the IP address (`$REGISTRY_IP`) of the docker container and the port (`$REGISTRY_PORT`) it's listening on, and that, if it is an insecure Docker Registry, the Docker Daemon has been configured to trust the insecure registry. We also assume that you have authenticated to the registry if that is required in your environment. And, very important, we also assume that this is either a public Docker Registry accessible to your Kubernetes deployment or that it's the internal Docker Registry used by your Kubernetes install.

See [HACKING.md](../../HACKING.md) for more information on setting up the local development environment with all the pieces in place.


Now deploy it as a Kubernetes pod:

    kubectl run -ti --attach keycloak-import --image=$REGISTRY_IP:$REGISTRY_PORT/strimzi/keycloak-import

The continer will perform the imports of realms into the Keycloak server, and exit. If you run `kubectl get pod` you'll see it CrashLoopBackOff because as soon as it's done, Kubernetes will restart the pod in the background, which will try to execute the same imports again, and fail. You'll also see errors in the Keycloak log, but as long as the initial realm import was successful, you can safely ignore them.

Remove the `keycloak-import` deployment:

    kubectl delete deployment keycloak-import


### Deploying the Kafka cluster

Assuming you have already installed Strimzi Kafka Operator, you can now simply deploy one of the `kafka-oatuh-*` yaml files. All examples are configured with OAuth2 for authentication.

For example:

    kubectl apply -f kafka-oauth-single-authz.yaml


