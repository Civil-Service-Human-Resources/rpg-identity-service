package uk.gov.cshr.useraccount.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.Charset;
import java.util.List;
import javax.inject.Inject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockitoTestExecutionListener;
import org.springframework.http.MediaType;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import uk.gov.cshr.useraccount.UserAccountServiceApplication;
import uk.gov.cshr.useraccount.model.AzureUser;
import uk.gov.cshr.useraccount.model.UserDetails;
import uk.gov.cshr.useraccount.service.AzureUserAccountService;

@Ignore
@ActiveProfiles("dev")
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, classes = UserAccountServiceApplication.class)
@ContextConfiguration
@WebAppConfiguration
@TestExecutionListeners(MockitoTestExecutionListener.class)
public class UserAccountTests extends AbstractTestNGSpringContextTests {

    final private MediaType APPLICATION_JSON_UTF8 = new MediaType(MediaType.APPLICATION_JSON.getType(),
            MediaType.APPLICATION_JSON.getSubtype(),
            Charset.forName("utf8"));

    @Value("${spring.test.testEmailAccount}")
    private String testEmailAccount;

    @Inject
    private WebApplicationContext webApplicationContext;

    @Autowired
    private AzureUserAccountService azureUserAccountService;

    private MockMvc mockMvc;


    @Before
    public void before() {

        this.mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @After
    public void after() {
    }

	@Test
    public void testCreateAccount() throws Exception {

        List<AzureUser> azureUsers = azureUserAccountService.getUsers();
        int azureUsersSize = azureUsers.size();

        ObjectMapper objectMapper = new ObjectMapper();

        String userName = "testuser" + azureUsersSize + 1;

        UserDetails userDetails = UserDetails.builder()
				.userName(userName)
				.password("1234qwerQWER")
				.emailAddress(testEmailAccount)
				.build();

        String json = objectMapper.writeValueAsString(userDetails);

        MvcResult mvcResult = this.mockMvc.perform(post("/useraccount/create")
				.with(user("crudusername").password("crudpassword").roles("CRUD_ROLE"))
                .contentType(APPLICATION_JSON_UTF8)
                .content(json)
                .accept(APPLICATION_JSON_UTF8))
                .andExpect(status().isCreated())
                .andReturn();

        AzureUser azureUser = azureUserAccountService.getUser(mvcResult.getResponse().getContentAsString());
        Assert.assertEquals("user created", userName, azureUser.getDisplayName());
        
        this.mockMvc.perform(patch("/useraccount/enable/" + azureUser.getId())
				.with(user("crudusername").password("crudpassword").roles("CRUD_ROLE"))
                .contentType(APPLICATION_JSON_UTF8)
                .content(json)
                .accept(APPLICATION_JSON_UTF8))
                .andExpect(status().isAccepted())
                .andReturn();

        mvcResult = this.mockMvc.perform(post("/useraccount/create")
				.with(user("crudusername").password("crudpassword").roles("CRUD_ROLE"))
                .contentType(APPLICATION_JSON_UTF8)
                .content(json)
                .accept(APPLICATION_JSON_UTF8))
                .andExpect(status().isImUsed())
                .andReturn();
    }
}
