package uk.gov.cshr.useraccount.exceptions;

public class NameAlreadyExistsException extends Exception {

    public NameAlreadyExistsException(String name) {
        super(name);
    }
}
