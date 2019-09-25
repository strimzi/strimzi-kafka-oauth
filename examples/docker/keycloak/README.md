Keycloak
========

This project builds and runs Keycloak docker container.


Running without SSL
-------------------

From `docker` directory run:

    docker-compose -f compose.yml -f keycloak/compose.yml up --build 

You may want to delete any previous instances by using:

    docker rm -f keycloak
    
    
Running with SSL
----------------

From `docker` directory run:

    docker-compose -f compose.yml -f keycloak/compose-ssl.yml up --build
     
A keystore is pre-generated in `certificates` sub-directory.
There is also a pre-generated CA root certificate, used to sign server certificate, in `../certificates`:


Regenerating server certificate
-------------------------------

If you want to regenerate a server keystore run the following:

    cd certificates
    rm *.srl *.p12 cert-*
    ./gen-keycloak-certs.sh

