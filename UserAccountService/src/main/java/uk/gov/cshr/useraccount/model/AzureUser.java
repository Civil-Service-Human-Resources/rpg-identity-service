package uk.gov.cshr.useraccount.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AzureUser implements Serializable {

//    private String id;
//    private Object businessPhones;
    private boolean accountEnabled;
    private String displayName;
    private String mailNickname;
    private String userPrincipalName;
    private PasswordProfile passwordProfile;
}
