package io.strimzi.kafka.oauth.validator;

public class TokenSignatureException extends TokenValidationException {

    {
        status(Status.INVALID_TOKEN);
    }

    public TokenSignatureException() {
        super();
    }

    public TokenSignatureException(String message) {
        super(message);
    }

    public TokenSignatureException(String message, Throwable cause) {
        super(message, cause);
    }

    public TokenSignatureException(Throwable cause) {
        super(cause);
    }

    protected TokenSignatureException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
