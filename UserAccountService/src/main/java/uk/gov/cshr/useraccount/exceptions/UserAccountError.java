package uk.gov.cshr.useraccount.exceptions;

import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserAccountError implements Serializable {

    private static final long serialVersionUID = 1L;

    private HttpStatus status;
    private String message;
    private List<String> errors;
}
