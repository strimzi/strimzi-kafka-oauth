#!/bin/bash

[[ "$TRACE" ]] && set -x

: ${REALM:=KERBEROS}
: ${DOMAIN_REALM:=kerberos}
: ${KERB_MASTER_KEY:=masterkey}
: ${KERB_ADMIN_USER:=admin}
: ${KERB_ADMIN_PASS:=admin}
: ${KAFKA_USER:=kafka}
: ${KAFKA_HOST:=kafka}
: ${KAFKA_CLIENT_USER:=client}

fix_nameserver() {
  cat>/etc/resolv.conf<<EOF
nameserver $NAMESERVER_IP
search $SEARCH_DOMAINS
EOF
}

fix_hostname() {
  sed -i "/^hosts:/ s/ *files dns/ dns files/" /etc/nsswitch.conf
}

create_config() {
  : ${KDC_ADDRESS:=$(hostname -f)}

  cat>/etc/krb5.conf<<EOF
[logging]
 default = FILE:/var/log/kerberos/krb5libs.log
 kdc = FILE:/var/log/kerberos/krb5kdc.log
 admin_server = FILE:/var/log/kerberos/kadmind.log

[libdefaults]
 default_realm = $REALM
 dns_lookup_realm = false
 dns_lookup_kdc = false
 ticket_lifetime = 24h
 renew_lifetime = 7d
 forwardable = true

[realms]
 $REALM = {
  kdc = $KDC_ADDRESS
  admin_server = $KDC_ADDRESS
 }

[domain_realm]
 .$DOMAIN_REALM = $REALM
 $DOMAIN_REALM = $REALM
EOF
}

create_db() {
  kdb5_util -P $KERB_MASTER_KEY -r $REALM create -s
}

start_kdc() {
  service krb5-kdc start
  service krb5-admin-server start
}

restart_kdc() {
  service krb5-kdc restart
  service krb5-admin-server restart
}

create_admin_user() {
  kadmin.local -q "addprinc -pw $KERB_ADMIN_PASS $KERB_ADMIN_USER/admin"
  echo "*/admin@$REALM *" > /etc/krb5kdc/kadm5.acl
}

create_kafka_user() {
  kadmin.local -q "addprinc -randkey $KAFKA_HOST/$KAFKA_USER@$REALM"
  kadmin.local -q "ktadd -k /keytabs/kafka_broker.keytab $KAFKA_HOST/$KAFKA_USER@$REALM"
  kadmin.local -q "addprinc -randkey $KAFKA_HOST/$KAFKA_CLIENT_USER@$REALM"
  kadmin.local -q "ktadd -k /keytabs/kafka_client.keytab $KAFKA_HOST/$KAFKA_CLIENT_USER@$REALM"
  chmod 666 /keytabs/kafka_broker.keytab
  chmod 666 /keytabs/kafka_client.keytab
}



if [ ! -f /kerberos_initialized ]; then
  mkdir -p /var/log/kerberos
  create_config
  create_db
  create_admin_user
  create_kafka_user
  start_kdc

  touch /kerberos_initialized
else
  start_kdc
fi

tail -F /var/log/kerberos/krb5kdc.log
