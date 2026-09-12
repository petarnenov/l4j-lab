package dev.l4jlab.chain.web;

/** A malformed or unknown company or period. Becomes a 400 naming the offending field. */
public class InvalidSelectionException extends RuntimeException {

    private final String field;

    public InvalidSelectionException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
