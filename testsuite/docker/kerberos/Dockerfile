FROM ubuntu

RUN DEBIAN_FRONTEND=noninteractive apt-get update -y && apt-get install -y krb5-kdc krb5-admin-server

EXPOSE 88 749

ADD ./config.sh /config.sh

ENTRYPOINT ["/config.sh"]

