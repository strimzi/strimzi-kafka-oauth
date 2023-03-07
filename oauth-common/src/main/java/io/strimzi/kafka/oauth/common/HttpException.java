/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.common;

import java.net.URI;

/**
 * An exception that signals an error status while performing a HTTP request.
 * When this exception is encountered it means the client successfully established the connection with the server,
 * established any secure (https) session, and received an HTTP response from the server.
 */
public class HttpException extends RuntimeException {

    /**
     * HTTP method
     */
    private final String method;

    /**
     * Target URL
     */
    private final URI uri;

    /**
     * HTTP Response status
     */
    private final int status;

    /**
     * An HTTP response body
     */
    private final String response;

    /**
     * Create a new instance
     *
     * @param method An HTTP method used
     * @param uri A target url
     * @param status The HTTP response status code
     * @param response The HTTP response body
     */
    public HttpException(String method, URI uri, int status, String response) {
        super(method + " request to " + uri + " failed with status " + status + ": " + response);

        this.method = method;
        this.uri = uri;
        this.status = status;
        this.response = response;
    }

    /**
     * Get the HTTP method
     *
     * @return The HTTP method
     */
    public String getMethod() {
        return method;
    }

    /**
     * Get the target url
     *
     * @return A URI object for the url
     */
    public URI getUri() {
        return uri;
    }

    /**
     * Get the HTTP status
     *
     * @return A status code as int
     */
    public int getStatus() {
        return status;
    }

    /**
     * Get the HTTP response body
     *
     * @return A response body
     */
    public String getResponse() {
        return response;
    }
}
