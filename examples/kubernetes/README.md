Examples of Strimzi Kafka Cluster with OAuth
--------------------------------------------

Here are several examples of Kafka Cluster definitions for deployment with Strimzi Cluster Operator.
They assume Keycloak is used as an authorization server, with properly configured realms called `demo`, and `kafka-authz`.

For details of how these examples use Keycloak Authorization Services with Kafka, including the authorization model and permissions setup, see [Using Keycloak Authorization Services with Strimzi Kafka OAuth](./README-keycloak-authorization.md)


* `keycloak.yaml`

  A Keycloak pod you can use to start an ephemeral instance of Keycloak. Any changes to realms will be lost when the pod shuts down.

* `keycloak-postgres.yaml`, `postgres.yaml`, `postgres-pvc.yaml`, `keycloak-realms-configmap.yaml`

  An alternative to `keycloak.yaml`, this set of yamls deploys a standalone persistent instance of Keycloak.

* `kafka-oauth-single.yaml`

  A single node Kafka cluster with OAuth 2 authentication using the 'demo' realm, and fast local signature validation (with keys loaded from the JWKS endpoint) for validating access tokens.

* `kafka-oauth-single-introspect.yaml`

  A single node Kafka cluster with OAuth 2 authentication using the `demo` realm, and introspection endpoint for access token validation. It requires that a secret is first deployed:

      kubectl create secret generic my-cluster-oauth-client-secret --from-literal=clientSecret=kafka-broker-secret

* `kafka-oauth-single-authz.yaml`

  A single node Kafka cluster with OAuth 2 authentication using the `kafka-authz` realm, a fast local signature validation, and Keycloak Authorization Services for token-based authorization.

* `kafka-oauth-over-plain-single-authz.yaml`

  A single node Kafka cluster with OAuth over PLAIN enabled.

* `kafka-oauth-single-authz-metrics.yaml`

  A single node Kafka cluster with OAuth 2 authentication with OAuth metrics enabled.
  See the [Metrics example](./README-metrics.md) for instructions on how to setup this example.

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
You can typically make it accessible on 'http://localhost:8080' by using `kubectl port-forward`:

    kubectl port-forward keycloak 8080


#### Deploying the Postgres and Keycloak that stores state to Postgres

First, we need a persistent filesystem that is remounted if the Postgres pod is deleted, and recreated:

    kubectl apply -f postgres-pvc.yaml
    
Then, we need to start the Postgres:

    kubectl apply -f postgres.yaml

Deploy the mountable realm import files for Keycloak:

    kubectl apply -f keycloak-realms-configmap.yaml
    
And finally, start the Keycloak pod that uses Postgres:

    kubectl apply -f keycloak-postgres.yaml

Note: The script assumes that the postgres was deployed in `myproject` namespace. If you deploy it to some other namespace
e.g. `default` you can fix the script on the fly:

    cat keycloak-postgres.yaml | sed -e 's/myproject/default/'  | kubectl apply -f -


#### Minishift

    KEYCLOAK_HOST=$(minishift ip)
    KEYCLOAK_PORT=$(kubectl get svc | grep keycloak | awk -F '8080:' '{print $2}' | awk -F '/' '{print $1}')
    echo http://$KEYCLOAK_HOST:$KEYCLOAK_PORT/admin

You can then open the printed URL and login with admin:admin.


#### Minikube

You can connect directly to Kubernetes Node IP using a NodePort port:

    KEYCLOAK_HOST=$(minikube ip)
    KEYCLOAK_PORT=$(kubectl get svc | grep keycloak | awk -F '8080:' '{print $2}' | awk -F '/' '{print $1}')
    echo http://$KEYCLOAK_HOST:$KEYCLOAK_PORT/admin

You can then open the printed URL and login with admin:admin.


#### Kubernetes Kind

In order to connect to Keycloak Admin Console you have to create a TCP tunnel:

    kubectl port-forward svc/keycloak 8080:8080
    
You can then open: http://localhost:8080/admin and login with admin:admin.    


### Importing example realms

If you use the `keycloak-postgres.yaml` example with the `keycloak-realms-configmap.yaml` file to provide the mounted realm files, then the realms are imported automatically when the Keycloak is started.

Alternatively, you can use Keycloak Admin Console GUI to import the realm. There are two realm JSON files in [../docker/keycloak/realms] directory which you can import one by one.

Otherwise, you can automate the import by creating the pod job that uses Keycloak Admin API to perform the import.
The usage of `keycloak-realms-configmap.yaml` is a much simpler approach to importing the realm.

### Deploying the Kafka cluster

Assuming you have already installed Strimzi Kafka Operator, you can now simply deploy one of the `kafka-oauth-*` yaml files. All examples are configured with OAuth2 for authentication.

For example:

    kubectl apply -f kafka-oauth-single-authz.yaml


