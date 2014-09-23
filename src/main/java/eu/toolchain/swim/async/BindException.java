package eu.toolchain.swim.async;

public class BindException extends Exception {
    public BindException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public BindException(final String message) {
        super(message);
    }

    public BindException(final Throwable cause) {
        super(cause);
    }
}
