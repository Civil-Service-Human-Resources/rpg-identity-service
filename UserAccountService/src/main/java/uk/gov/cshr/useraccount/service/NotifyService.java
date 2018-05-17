package uk.gov.cshr.useraccount.service;

import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.gov.service.notify.NotificationClient;
import uk.gov.service.notify.NotificationClientException;

@Service
public class NotifyService {

    @Value("${spring.notifyservice.templateid}")
    private String templateId;

    @Value("${spring.notifyservice.notifyApiKey}")
    private String notifyApiKey;
   
    @Value("${spring.notifyservice.accountEnableURL}")
    private String accountEnableURL;

    public void emailEnableAccountCode(String emailAddress, String userID) throws NotificationClientException {

        NotificationClient client = new NotificationClient(notifyApiKey);

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(String.format(accountEnableURL, userID));

        Map<String, String> personalisation = new HashMap<>();
        personalisation.put("message", stringBuilder.toString());
        client.sendEmail(templateId, emailAddress, personalisation, "emailEnableAccountCode");
    }
}
