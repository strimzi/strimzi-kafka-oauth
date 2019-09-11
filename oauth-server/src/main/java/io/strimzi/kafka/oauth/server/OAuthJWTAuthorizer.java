/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.server;

import kafka.network.RequestChannel;
import kafka.security.auth.Acl;
import kafka.security.auth.Authorizer;
import kafka.security.auth.Operation;
import kafka.security.auth.Resource;
import org.apache.kafka.common.security.auth.KafkaPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.collection.immutable.Map;
import scala.collection.immutable.Set;

public class OAuthJWTAuthorizer implements Authorizer {

    private static final Logger log = LoggerFactory.getLogger(OAuthJWTAuthorizer.class);

    @Override
    public void configure(java.util.Map<String, ?> map) {
        log.warn("configure() " + map);
    }

    @Override
    public boolean authorize(RequestChannel.Session session, Operation operation, Resource resource) {
        log.warn("authorize() session: " + session + ", operation: " + operation + ", resource: " + resource);
        return true;
    }

    @Override
    public void addAcls(Set<Acl> acls, Resource resource) {
        log.warn("addAcls() acls: " + acls + ", resource: " + resource);
    }

    @Override
    public boolean removeAcls(Set<Acl> acls, Resource resource) {
        log.warn("removeAcls() acls: " + acls + ", resource: " + resource);
        return true;
    }

    @Override
    public boolean removeAcls(Resource resource) {
        log.warn("removeAcls() resource: " + resource);
        return true;
    }

    @Override
    public Set<Acl> getAcls(Resource resource) {
        log.warn("getAcls() resource: " + resource);
        return null;
    }

    @Override
    public Map<Resource, Set<Acl>> getAcls(KafkaPrincipal principal) {
        log.warn("getAcls() principal: " + principal);
        return null;
    }

    @Override
    public Map<Resource, Set<Acl>> getAcls() {
        log.warn("getAcls()");
        return null;
    }

    @Override
    public void close() {
        log.warn("close()");
    }
}
