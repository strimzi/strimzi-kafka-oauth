#!/bin/bash
set -e

# create hydra server private key
openssl genrsa -out hydra.key 2048

# create certificate-signing request
openssl req -new -sha256 \
   -key hydra.key \
   -subj "/CN=hydra" \
   -reqexts SAN \
   -config <(cat /etc/ssl/openssl.cnf \
       <(printf "\n[SAN]\nsubjectAltName=DNS:hydra,DNS:hydra.local")) \
   -out hydra.csr


# Verify CSR content
#openssl req -in hydra.csr -noout -text

# Generate the certificate
openssl x509 -req -in hydra.csr -CA ../../certificates/ca.crt -CAkey ../../certificates/ca.key -CAcreateserial -out hydra.crt -days 3650 -sha256

# Verify the certificate's content
#openssl x509 -in hydra.crt -text -noout

