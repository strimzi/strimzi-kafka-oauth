/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.common;

import java.net.URI;

public class HttpException extends RuntimeException {

    private final String method;
    private final URI uri;
    private final int status;
    private final String response;

    public HttpException(String method, URI uri, int status, String response) {
        super(method + " request to " + uri + " failed with status " + status + ": " + response);

        this.method = method;
        this.uri = uri;
        this.status = status;
        this.response = response;
    }

    public String getMethod() {
        return method;
    }

    public URI getUri() {
        return uri;
    }

    public int getStatus() {
        return status;
    }

    public String getResponse() {
        return response;
    }
}
