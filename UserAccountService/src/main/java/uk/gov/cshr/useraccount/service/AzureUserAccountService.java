package uk.gov.cshr.useraccount.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import uk.gov.cshr.useraccount.exceptions.NameAlreadyExistsException;
import uk.gov.cshr.useraccount.model.AzureUser;
import uk.gov.cshr.useraccount.model.PasswordProfile;
import uk.gov.cshr.useraccount.model.UserDetails;
import uk.gov.service.notify.NotificationClientException;

@Service
public class AzureUserAccountService {

    private static final Logger log = LoggerFactory.getLogger(AzureUserAccountService.class);

    private String accessToken;

    private Date tokenExpiryDate;

	@Value("${spring.azure.activedirectory.usersURL}")
    private  String usersURL;

	@Value("${spring.azure.activedirectory.oauthURL}")
    private String oauthURL;

    @Value("${spring.azure.activedirectory.client-id}")
    private String clientID;

    @Value("${spring.azure.activedirectory.client-secret}")
    private String clientSecret;

	@Value("${spring.azure.activedirectory.resourceURL}")
    private String resourceURL;

	@Value("${spring.azure.activedirectory.tenant}")
	private String tenant;

    @Autowired
    private NotifyService notifyService;

    /**
     * Return newly created ID
     * @param userDetails
     * @return
     * @throws uk.gov.cshr.useraccount.exceptions.NameAlreadyExistsException
     */
    public String create(UserDetails userDetails) throws NameAlreadyExistsException {

        List<AzureUser> existingUsers = getUsers();
        for (AzureUser existingUser : existingUsers) {
            if ( existingUser.getUserPrincipalName().equals(userDetails.getUserName() + "@" + tenant) ) {
                log.debug(userDetails.getUserName() + "@" + tenant + " exists");
                throw new NameAlreadyExistsException(userDetails.getUserName());
            }
        }

        try {
            AzureUser azureUser = AzureUser.builder()
                    .accountEnabled(Boolean.FALSE)
                    .displayName(userDetails.getUserName())
                    .mailNickname(userDetails.getUserName())
                    .userPrincipalName(userDetails.getUserName() + "@" + tenant)
                    .passwordProfile(new PasswordProfile(userDetails.getPassword(), false))
                    .build();

            ObjectMapper objectMapper = new ObjectMapper();
            String json = objectMapper.writeValueAsString(azureUser);

            RestTemplate restTemplate = new RestTemplate();

            HttpHeaders headers = new HttpHeaders();
            headers.add("Authorization", getAccessToken());
            headers.add("Content-Type", MediaType.APPLICATION_JSON_UTF8_VALUE);
            HttpEntity<String> entity = new HttpEntity<>(json, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    String.format(usersURL, tenant), HttpMethod.POST, entity, String.class);

            JSONObject jsonObject = new JSONObject(response.getBody());
            String userID = jsonObject.getString("id");

            notifyService.emailEnableAccountCode(userDetails.getEmailAddress(), userID, userDetails.getUserName());

            return userID;

        }
        catch (JsonProcessingException | JSONException | RestClientException | NotificationClientException e) {
            throw new RuntimeException(e);
        }
    }

	public ResponseEntity<String> delete(String userID) {

		HttpHeaders headers = new HttpHeaders();
		headers.add("Authorization", getAccessToken());
		headers.add("Content-Type", "application/json");
		HttpEntity<String> entity = new HttpEntity<>(headers);
		RestTemplate restTemplate = new RestTemplate();
		return restTemplate.exchange(
                String.format(usersURL, tenant) + "/" + userID, HttpMethod.DELETE, entity, String.class);
	}

    public List<AzureUser> getUsers() {

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.add("Authorization", getAccessToken());
            headers.add("Content-Type", "application/json");
            HttpEntity<String> entity = new HttpEntity<>(headers);
            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<String> response = restTemplate.exchange(
                    String.format(usersURL, tenant), HttpMethod.GET, entity, String.class);
            
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode responseNode = objectMapper.readTree(response.getBody());
            JsonNode usersNode = objectMapper.readTree(responseNode.get("value").toString());

            Iterator<JsonNode> nodeIterator = usersNode.iterator();

            List<AzureUser> azureUsers = new ArrayList<>();

            while( nodeIterator.hasNext() ) {
                azureUsers.add(objectMapper.readValue(nodeIterator.next().toString(), AzureUser.class));
            }

            return azureUsers;
        }
        catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    public AzureUser getUser(String userID) {

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.add("Authorization", getAccessToken());
            headers.add("Content-Type", "application/json");
            HttpEntity<String> entity = new HttpEntity<>(headers);
            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<String> response = restTemplate.exchange(
                    String.format(usersURL + "/" + userID, tenant), HttpMethod.GET, entity, String.class);

            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode responseNode = objectMapper.readTree(response.getBody());
            return objectMapper.readValue(responseNode.toString(), AzureUser.class);
        }
        catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private String getAccessToken() {

        if (accessToken != null && tokenExpiryDate.after(new Date())) {
            return accessToken;
        }
        else {
            try {
                String body = "grant_type=client_credentials";
                body += "&client_id=" + clientID;
                body += "&client_secret=" + clientSecret;
                body += "&resource=" + resourceURL;

                RequestEntity request = RequestEntity.post(
                        new URI(String.format(oauthURL, tenant)))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(body);

                ResponseEntity<String> response = new RestTemplate().exchange(request, String.class);

                if ( response.getStatusCode().equals(HttpStatus.OK) ) {

                    JSONObject json = new JSONObject(response.getBody());
                        log.debug("getAccessToken json=" + json);
                        tokenExpiryDate = new Date(json.getLong("expires_on"));

                    return json.getString("access_token");
                }
                else {
                    throw new RuntimeException("Could not get access token");
                }
            }
            catch (URISyntaxException | JSONException | RestClientException ex ) {
                throw new RuntimeException("Could not get access token", ex);
            }
        }
    }

    public ResponseEntity<String> enableUser(String userID) {

        RestTemplate restTemplate = new RestTemplate();
        HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory();
        requestFactory.setConnectTimeout(10000);
        requestFactory.setReadTimeout(10000);

        restTemplate.setRequestFactory(requestFactory);

        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", getAccessToken());
        headers.add("Content-Type", "application/json");

        String json = "{\"accountEnabled\":true}";

        HttpEntity<String> entity = new HttpEntity<>(json, headers);
        return restTemplate.exchange(
                String.format(usersURL + "/" + userID, tenant), HttpMethod.PATCH, entity, String.class);
    }
}
