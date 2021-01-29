#!/bin/bash
set -e

gen_certs() {
  NAME=$1

  # create hydra server private key
  openssl genrsa -out $NAME.key 2048

  # create certificate-signing request
  openssl req -new -sha256 \
   -key $NAME.key \
   -subj "/CN=$NAME" \
   -reqexts SAN \
   -config <(cat /etc/ssl/openssl.cnf \
       <(printf "\n[SAN]\nsubjectAltName=DNS:$NAME,DNS:$NAME.local")) \
   -out $NAME.csr


  # Verify CSR content
  #openssl req -in $NAME.csr -noout -text

  # Generate the certificate
  openssl x509 -req -in $NAME.csr -CA ca.crt -CAkey ca.key -CAcreateserial -out $NAME.crt -days 3650 -sha256

  # Verify the certificate's content
  #openssl x509 -in $NAME.crt -text -noout
}

gen_certs hydra
gen_certs hydra-jwt