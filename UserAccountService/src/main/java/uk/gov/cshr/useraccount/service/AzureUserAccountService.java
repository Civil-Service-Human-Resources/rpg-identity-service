package uk.gov.cshr.useraccount.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.aad.adal4j.AuthenticationContext;
import com.microsoft.aad.adal4j.AuthenticationException;
import com.microsoft.aad.adal4j.AuthenticationResult;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Pattern;
import javax.naming.ServiceUnavailableException;
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
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import uk.gov.cshr.useraccount.exceptions.InvalidPasswordException;
import uk.gov.cshr.useraccount.exceptions.NameAlreadyExistsException;
import uk.gov.cshr.useraccount.model.AzureUser;
import uk.gov.cshr.useraccount.model.PasswordProfile;
import uk.gov.cshr.useraccount.model.UserAccount;
import uk.gov.cshr.useraccount.model.UserDetails;
import uk.gov.cshr.useraccount.repository.UserAccountRepository;
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

    @Value("${spring.azure.activedirectory.native-client-id}")
    private String nativeClientID;

    @Value("${spring.azure.activedirectory.web-client-id}")
    private String webClientID;

    @Value("${spring.azure.activedirectory.web-client-secret}")
    private String webClientSecret;

	@Value("${spring.azure.activedirectory.resourceURL}")
    private String resourceURL;

	@Value("${spring.azure.activedirectory.tenant}")
	private String tenant;

    @Value("${spring.azure.activedirectory.authority}")
	private String authority;

    @Autowired
    private NotifyService notifyService;

    @Autowired
    private UserAccountRepository userAccountRepository;

    /**
     * Return newly created ID
     * @param userDetails
     * @return
     * @throws uk.gov.cshr.useraccount.exceptions.NameAlreadyExistsException
     */
    public AzureUser create(UserDetails userDetails) throws NameAlreadyExistsException, InvalidPasswordException {

        List<String> errors = new ArrayList<>();

        if ( ! isValidPassword(userDetails.getPassword(), errors) ) {
            throw new InvalidPasswordException(errors);
        }

		String name = userDetails.getEmailAddress().replace("@", "");
        String principalName = name + "@" + tenant;

        UserAccount existingUserAccount = userAccountRepository.findByEmail(userDetails.getEmailAddress());

        if ( existingUserAccount != null ) {
            log.debug(userDetails.getEmailAddress() + " exists");
            throw new NameAlreadyExistsException(userDetails.getEmailAddress());
        }

        existingUserAccount = userAccountRepository.findByUsername(name);

        if ( existingUserAccount != null ) {
            log.debug(userDetails.getEmailAddress() + " exists");
            throw new NameAlreadyExistsException(userDetails.getEmailAddress());
        }

        try {

            AzureUser azureUser = AzureUser.builder()
                    .accountEnabled(Boolean.FALSE)
                    .displayName(principalName)
                    .mailNickname(name)
                    .userPrincipalName(principalName)
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

            azureUser.setId(userID);

            UserAccount userAccount = UserAccount.builder()
                    .email(userDetails.getEmailAddress())
                    .userid(userID)
                    .username(principalName)
                    .name(userDetails.getName())
                    .build();

            userAccountRepository.save(userAccount);

            try {
                notifyService.emailEnableAccountCode(userDetails.getEmailAddress(), userID);
            }
            catch(NotificationClientException e) {
                e.printStackTrace();
            }


            return azureUser;

        }
        catch (JsonProcessingException | JSONException | RestClientException  e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean isValidPassword(String password, List<String> errorList) {

        Pattern specailCharPatten = Pattern.compile("[^a-z0-9 ]", Pattern.CASE_INSENSITIVE);
        Pattern UpperCasePatten = Pattern.compile("[A-Z ]");
        Pattern lowerCasePatten = Pattern.compile("[a-z ]");
        Pattern digitCasePatten = Pattern.compile("[0-9 ]");
        errorList.clear();

        boolean flag = true;

        if (password.length() < 8) {
            errorList.add("Password lenght must have alleast 8 character.");
            flag=false;
        }
        if (! (specailCharPatten.matcher(password).find() || digitCasePatten.matcher(password).find() ) ) {
            errorList.add("Password must have at least one special character or digit.");
            flag=false;
        }
        if (!UpperCasePatten.matcher(password).find()) {
            errorList.add("Password must have at least one uppercase character.");
            flag=false;
        }
        if (!lowerCasePatten.matcher(password).find()) {
            errorList.add("Password must have at least one lowercase character.");
            flag=false;
        }

        return flag;
    }

	public void delete(String userID) {

		HttpHeaders headers = new HttpHeaders();
		headers.add("Authorization", getAccessToken());
		headers.add("Content-Type", "application/json");
		HttpEntity<String> entity = new HttpEntity<>(headers);
		RestTemplate restTemplate = new RestTemplate();
		ResponseEntity<String> response = restTemplate.exchange(
                String.format(usersURL, tenant) + "/" + userID, HttpMethod.DELETE, entity, String.class);

        UserAccount userAccount = userAccountRepository.findByUserid(userID);
        userAccountRepository.delete(userAccount);
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

    public AzureUser getUser(String userIDPrefix) {

        UserAccount userAccount = userAccountRepository.findByUseridStartsWith(userIDPrefix);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.add("Authorization", getAccessToken());
            headers.add("Content-Type", "application/json");
            HttpEntity<String> entity = new HttpEntity<>(headers);
            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<String> response = restTemplate.exchange(
                    String.format(usersURL + "/" + userAccount.getUserid(), tenant), HttpMethod.GET, entity, String.class);

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
                body += "&client_id=" + webClientID;
                body += "&client_secret=" + webClientSecret;
                body += "&resource=" + resourceURL;

                URI uri = new URI(String.format(oauthURL, tenant));

                ClientHttpRequest request = requestFactory.createRequest(uri, HttpMethod.POST);
                request.getBody().write(body.getBytes());
                request.getBody().flush();
                request.getBody().close();

                ClientHttpResponse response = request.execute();
                System.out.println("getAccessToken =" + response.getStatusCode() );

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

    public void enable(String userIDPrefix) {

        AzureUser azureUser = getUser(userIDPrefix);
        azureUser.setAccountEnabled(true);
        updateUser(azureUser.getId(), azureUser);

    }

    public void updateUser(String userID, AzureUser azureUser) {

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
        ResponseEntity<String> response = restTemplate.exchange(
                String.format(usersURL + "/" + userID, tenant), HttpMethod.PATCH, entity, String.class);

        if ( response.getStatusCode() != HttpStatus.NO_CONTENT ) {
            throw new RuntimeException("Account could not be enabled");
        }
    }

    public String authenticate(String email, String password) {

        try {
            UserAccount userAccount = userAccountRepository.findByEmail(email);
            AuthenticationResult authenticationResult = getUserAccessTokenFromUserCredentials(
                    userAccount.getUsername(), password);
            return authenticationResult.getUserInfo().getUniqueId();
        }
        catch (MalformedURLException | ServiceUnavailableException | InterruptedException | ExecutionException ex) {

            if ( ex.getCause() instanceof AuthenticationException) {
                throw (RuntimeException)ex.getCause();
            }
            else {
                throw new RuntimeException(ex);
            }
        }
    }

    private AuthenticationResult getUserAccessTokenFromUserCredentials(
            String username, String password) throws
            MalformedURLException, ServiceUnavailableException, InterruptedException, ExecutionException {

        AuthenticationContext context;
        AuthenticationResult result;
        ExecutorService service = null;
        try {
            service = Executors.newFixedThreadPool(1);
            context = new AuthenticationContext(authority, false, service);
            Future<AuthenticationResult> future = context.acquireToken("https://graph.microsoft.com", nativeClientID, username, password,
                    null);
            result = future.get();
        }
        finally {
            service.shutdown();
        }

        if (result == null) {
            throw new ServiceUnavailableException(
                    "authentication result was null");
        }
        return result;
    }

    private static String getUserInfoFromGraph(String accessToken) throws IOException {

        URL url = new URL("https://graph.microsoft.com/v1.0/me");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);
        conn.setRequestProperty("Accept","application/json");

        int httpResponseCode = conn.getResponseCode();
        if(httpResponseCode == 200) {
            BufferedReader in = null;
            StringBuilder response;
            try{
                in = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()));
                String inputLine;
                response = new StringBuilder();
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
            } finally {
                in.close();
            }
            return response.toString();
        } else {
            return String.format("Connection returned HTTP code: %s with message: %s",
                    httpResponseCode, conn.getResponseMessage());
        }
    }
}
