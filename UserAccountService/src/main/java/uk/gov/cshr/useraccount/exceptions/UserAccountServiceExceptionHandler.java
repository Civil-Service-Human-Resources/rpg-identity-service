package uk.gov.cshr.useraccount.exceptions;

import com.microsoft.aad.adal4j.AuthenticationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@ControllerAdvice
public class UserAccountServiceExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(UserAccountServiceExceptionHandler.class);

    @ExceptionHandler({RuntimeException.class})
    public ResponseEntity<Object> handleRuntimeException(RuntimeException ex, WebRequest request) {
        log.error(ex.getMessage(), ex);
        UserAccountError error = new UserAccountError(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), null);
        return handleExceptionInternal(ex, error, new HttpHeaders(), error.getStatus(), request);
    }

    @ExceptionHandler({AccessDeniedException.class})
    public ResponseEntity<Object> handleAccessDeniedExceptionException(AccessDeniedException ex, WebRequest request) {
        log.error(ex.getMessage(), ex);
        UserAccountError error = new UserAccountError(HttpStatus.FORBIDDEN, ex.getMessage(), null);
        return handleExceptionInternal(ex, error, new HttpHeaders(), error.getStatus(), request);
    }

    @ExceptionHandler({NameAlreadyExistsException.class})
    public ResponseEntity<Object> handleNameAlreadyExsistsException(NameAlreadyExistsException ex, WebRequest request) {
        log.error(ex.getMessage(), ex);
        UserAccountError error = new UserAccountError(HttpStatus.IM_USED, ex.getMessage(), null);
        return handleExceptionInternal(ex, error, new HttpHeaders(), error.getStatus(), request);
    }

    @ExceptionHandler({AuthenticationException.class})
    public ResponseEntity<Object> handleAuthenticationException(AuthenticationException ex, WebRequest request) {
        log.error(ex.getMessage(), ex);
        UserAccountError error = new UserAccountError(HttpStatus.FORBIDDEN, ex.getMessage(), null);
        return handleExceptionInternal(ex, error, new HttpHeaders(), error.getStatus(), request);
    }
}
