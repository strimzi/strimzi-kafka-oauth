Demo Realm Import
=================

This project builds and runs docker container that imports a demo realm into seperately running Keycloak service.


Running without SSL
-------------------

From `docker` directory run:

    docker-compose -f compose.yml -f keycloak-import/compose.yml up --build 

You may want to delete any previous instances by using:

    docker rm -f keycloak-import


Running with SSL
----------------

From `docker` directory run:

    docker-compose -f compose.yml -f keycloak-import/compose-ssl.yml up --build
     

