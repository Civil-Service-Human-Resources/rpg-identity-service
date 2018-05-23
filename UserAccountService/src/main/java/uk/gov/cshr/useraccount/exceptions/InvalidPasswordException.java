package uk.gov.cshr.useraccount.exceptions;

import java.util.List;

public class InvalidPasswordException extends Exception {

    private final List<String> errors;

    public InvalidPasswordException(List<String> errors) {
        this.errors = errors;
    }

    public List<String> getErrors() {
        return errors;
    }
}
