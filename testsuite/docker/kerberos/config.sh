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
: ${KDC_ADDRESS:=$(hostname -f)}
: ${KEYTABS_SHARED_DIR:=/keytabs}



create_config() {
  cat>/etc/krb5.conf<<EOF
[libdefaults]
 default_realm = KERBEROS
 dns_lookup_realm = false
 dns_lookup_kdc = false
 ticket_lifetime = 24h
 renew_lifetime = 7d
 forwardable = true
 rdns = false
 ignore_acceptor_hostname = true

[realms]
 KERBEROS = {
  kdc = kerberos:1088
  admin_server = kerberos
 }

[domain_realm]
 .kerberos = KERBEROS
 kerberos = KERBEROS
EOF
echo "Created config: /etc/krb5.conf"
cat /etc/krb5.conf
}


create_service_config() {
  cat>/etc/krb5kdc/kdc.conf<<EOF
[logging]
 default = FILE:/var/log/kerberos/krb5libs.log
 kdc = FILE:/var/log/kerberos/krb5kdc.log
 admin_server = FILE:/var/log/kerberos/kadmind.log

[kdcdefaults]
    kdc_ports = 1750,1088

[realms]
    KERBEROS = {
        database_name = /var/lib/krb5kdc/principal
        admin_keytab = FILE:/etc/krb5kdc/kadm5.keytab
        acl_file = /etc/krb5kdc/kadm5.acl
        key_stash_file = /etc/krb5kdc/stash
        kdc_ports = 1750,1088
        max_life = 10h 0m 0s
        max_renewable_life = 7d 0h 0m 0s
        #master_key_type = aes256-cts
        #supported_enctypes = aes256-cts:normal aes128-cts:normal
        #default_principal_flags = +preauth
    }
EOF
echo "Created service config: /etc/krb5kdc/kdc.conf"
cat /etc/krb5kdc/kdc.conf
}

create_db() {
  kdb5_util -P $KERB_MASTER_KEY -r $REALM create -s
  echo "Created db"
}

start_kdc() {
  service krb5-kdc start
  service krb5-admin-server start
}

create_admin_user() {
  kadmin.local -q "addprinc -pw $KERB_ADMIN_PASS $KERB_ADMIN_USER/admin"
  echo "*/admin@$REALM *" > /etc/krb5kdc/kadm5.acl
  echo "Created admin user in /etc/krb5kdc/kadm5.acl"
}

create_kafka_user() {
  kadmin.local -q "addprinc -randkey $KAFKA_HOST/$KAFKA_USER@$REALM"
  kadmin.local -q "ktadd -k /etc/krb5/kafka_broker.keytab $KAFKA_HOST/$KAFKA_USER@$REALM"
  kadmin.local -q "addprinc -randkey $KAFKA_HOST/$KAFKA_CLIENT_USER@$REALM"
  kadmin.local -q "ktadd -k /etc/krb5/kafka_client.keytab $KAFKA_HOST/$KAFKA_CLIENT_USER@$REALM"
  echo "Created keytab files for kafka user and kafka client:"
  ls -la /etc/krb5
  chmod 666 /etc/krb5/kafka_broker.keytab
  chmod 666 /etc/krb5/kafka_client.keytab
}

copy_keytab_files() {
  cp -r /etc/krb5/* $KEYTABS_SHARED_DIR
}

if [ ! -f /kerberos_initialized ]; then
  mkdir -p /var/log/kerberos
  mkdir /etc/krb5
  echo "Created directories:"
  ls -la /var/log/kerberos
  ls -la /etc/krb5

  create_service_config
  create_config
  create_db
  create_admin_user
  create_kafka_user
  copy_keytab_files
  start_kdc

  touch /kerberos_initialized
else
  start_kdc
fi

# Startup condition is based on the output of the log file
# See MockOAuthTests.java
tail -F /var/log/kerberos/krb5kdc.log
