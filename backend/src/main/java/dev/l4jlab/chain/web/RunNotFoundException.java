package dev.l4jlab.chain.web;

/** An unknown run identifier. Becomes a 404 naming the identifier. */
public class RunNotFoundException extends RuntimeException {

    public RunNotFoundException(String identifier) {
        super("No run exists with identifier '" + identifier + "'.");
    }
}
