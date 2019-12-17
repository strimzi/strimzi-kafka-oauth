/*
 * Copyright 2017-2019, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.oauth.common;

import java.net.URI;

public class HttpException extends RuntimeException {

    private URI uri;
    private int status;
    private String response;

    public HttpException(URI uri, int status, String response) {
        super("Request to " + uri + " failed with status " + status + ": " + response);

        this.uri = uri;
        this.status = status;
        this.response = response;
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
