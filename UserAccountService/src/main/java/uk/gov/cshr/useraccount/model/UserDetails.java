package uk.gov.cshr.useraccount.model;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserDetails implements Serializable {

    private String userName;
    private String emailAddress;
    private String password;
}
