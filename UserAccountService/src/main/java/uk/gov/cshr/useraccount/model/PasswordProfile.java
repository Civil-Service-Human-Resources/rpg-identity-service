package uk.gov.cshr.useraccount.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PasswordProfile {
    private String password;
    private boolean forceChangePasswordNextSignIn;
}
