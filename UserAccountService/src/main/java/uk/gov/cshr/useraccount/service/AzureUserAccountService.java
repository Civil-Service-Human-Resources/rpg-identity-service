package uk.gov.cshr.useraccount.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
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
     */
    public String create(UserDetails userDetails) {

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
            headers.add("Content-Type", "application/json");
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

	public void delete(String userID) {

		HttpHeaders headers = new HttpHeaders();
		headers.add("Authorization", getAccessToken());
		headers.add("Content-Type", "application/json");
		HttpEntity<String> entity = new HttpEntity<>(headers);
		RestTemplate restTemplate = new RestTemplate();
		ResponseEntity<String> response = restTemplate.exchange(
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

                ClientHttpRequestFactory requestFactory = getClientHttpRequestFactory();

                String body = "grant_type=client_credentials";
                body += "&client_id=" + clientID;
                body += "&client_secret=" + clientSecret;
                body += "&resource=" + resourceURL;

                URI uri = new URI(String.format(oauthURL, tenant));

                ClientHttpRequest request = requestFactory.createRequest(uri, HttpMethod.POST);
                request.getBody().write(body.getBytes());
                request.getBody().flush();
                request.getBody().close();

                ClientHttpResponse response = request.execute();
                InputStream responseInputStream = response.getBody();

                BufferedReader streamReader = new BufferedReader(
                        new InputStreamReader(responseInputStream, "UTF-8"));
                StringBuilder responseStrBuilder = new StringBuilder();

                String inputStr;
                while ((inputStr = streamReader.readLine()) != null) {
                    responseStrBuilder.append(inputStr);
                }
                JSONObject json = new JSONObject(responseStrBuilder.toString());
				log.debug("getAccessToken json=" + json);
                tokenExpiryDate = new Date(json.getLong("expires_on"));

                return json.getString("access_token");
            }
            catch (URISyntaxException | IOException | JSONException ex ) {
                throw new RuntimeException(ex);
            }
        }
    }

    private ClientHttpRequestFactory getClientHttpRequestFactory() {

        int timeout = 5000;
        HttpComponentsClientHttpRequestFactory clientHttpRequestFactory
                = new HttpComponentsClientHttpRequestFactory();
        clientHttpRequestFactory.setConnectTimeout(timeout);
        return clientHttpRequestFactory;
    }

    public void enable(String userID) {

        AzureUser azureUser = getUser(userID);
        azureUser.setAccountEnabled(true);
        updateUser(userID, azureUser);

    }

    public void updateUser(String userID, AzureUser azureUser) {

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            String json = objectMapper.writeValueAsString(azureUser);

            RestTemplate restTemplate = new RestTemplate();
            HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory();
            requestFactory.setConnectTimeout(10000);
            requestFactory.setReadTimeout(10000);

            restTemplate.setRequestFactory(requestFactory);

            HttpHeaders headers = new HttpHeaders();
            headers.add("Authorization", getAccessToken());
            headers.add("Content-Type", "application/json");

            json = "{\"accountEnabled\":true}";

            HttpEntity<String> entity = new HttpEntity<>(json, headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    String.format(usersURL + "/" + userID, tenant), HttpMethod.PATCH, entity, String.class);
        }
        catch (JsonProcessingException ex) {
            throw new RuntimeException(ex);
        }
    }
}
