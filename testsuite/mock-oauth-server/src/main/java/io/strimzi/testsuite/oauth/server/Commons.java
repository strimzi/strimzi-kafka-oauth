/*
 * Copyright 2017-2022, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.testsuite.oauth.server;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.http.HttpServerRequest;
import org.slf4j.Logger;
import org.slf4j.helpers.NOPLogger;

import java.util.Arrays;
import java.util.EnumSet;

import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;


public class Commons {

    private static final ThreadLocal<Logger> CONTEXT_LOG = new ThreadLocal<>();

    public static void setContextLog(Logger log) {
        CONTEXT_LOG.set(log);
    }

    public static Logger getContextLog() {
        Logger log = CONTEXT_LOG.get();
        return log != null ? log : NOPLogger.NOP_LOGGER;
    }

    @SafeVarargs
    public static <T extends Enum<T>> boolean isOneOf(T mode, T... modes) {
        return EnumSet.copyOf(Arrays.asList(modes)).contains(mode);
    }

    @SafeVarargs
    public static <T> boolean isOneOf(T mode, T... modes) {
        for (T element: modes) {
            if (element.equals(mode)) {
                return true;
            }
        }
        return false;
    }

    public static void sendResponse(HttpServerRequest req, HttpResponseStatus status) {
        sendResponse(req, status.code(), status.reasonPhrase());
    }

    public static void sendResponse(HttpServerRequest req, HttpResponseStatus status, String text) {
        sendResponse(req, status.code(), status.reasonPhrase(), text);
    }

    public static void sendResponse(HttpServerRequest req, int statusCode, String statusMessage) {
        getContextLog().info("< " + statusCode + " " + statusMessage);

        req.response().setStatusCode(statusCode)
            .setStatusMessage(statusMessage)
            .end();
    }

    public static void sendResponse(HttpServerRequest req, int statusCode, String statusMessage, String text) {
        getContextLog().info("< " + statusCode + " " + statusMessage + "\n" + text);

        req.response().setStatusCode(statusCode)
                .setStatusMessage(statusMessage)
                .end(text);
    }

    static void handleFailure(HttpServerRequest req, Throwable t, Logger log) {
        log.error("An error during processing of request: " + req, t);
        sendResponse(req, INTERNAL_SERVER_ERROR);
    }
}
