Hydra
=====

This project builds and runs Hydra docker container.


Running with SSL and opaque tokens
----------------------------------

By default Hydra issues non-JWT tokens that can't be locally introspected.

From `docker` directory run:

    docker-compose -f compose.yml -f hydra/compose.yml up --build
     
A keystore is pre-generated in `certificates` sub-directory.
There is also a pre-generated CA root certificate, used to sign server certificate, in `../certificates`:


Running with SSL and JWT tokens
-------------------------------

From `docker` directory run:

    docker-compose -f compose.yml -f hydra/compose-with-jwt.yml up --build
     
A keystore is pre-generated in `certificates` sub-directory.
There is also a pre-generated CA root certificate, used to sign server certificate, in `../certificates`:


Regenerating server certificate
-------------------------------

If you want to regenerate a server keystore run the following:

    cd certificates 
    rm *.srl *.crt *.key *.csr
    ./gen-hydra-certs.sh

