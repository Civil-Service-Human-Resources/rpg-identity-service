package uk.gov.cshr.useraccount.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import java.net.URI;
import java.util.List;
import javax.annotation.security.RolesAllowed;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import uk.gov.cshr.useraccount.exceptions.NameAlreadyExistsException;
import uk.gov.cshr.useraccount.exceptions.UserAccountError;
import uk.gov.cshr.useraccount.model.AzureUser;
import uk.gov.cshr.useraccount.model.UserDetails;
import uk.gov.cshr.useraccount.service.AzureUserAccountService;

@RestController
@RequestMapping(value = "/useraccount", produces = MediaType.APPLICATION_JSON_VALUE)
@ResponseBody
@Api(value = "useraccountservice")
@RolesAllowed("CRUD_ROLE")
public class UserAccountController {

	@Autowired
    private AzureUserAccountService userAccountService;

    @RequestMapping(method = RequestMethod.POST, value = "/create", produces = MediaType.TEXT_PLAIN_VALUE)
    @ApiOperation(value = "Create a User Account", nickname = "create")
    @ApiResponses(value = {
            @ApiResponse(code = 226, message = "The username already exists", response = UserAccountError.class)
    })
    public ResponseEntity<String> create(@RequestBody UserDetails userDetails) 
            throws NameAlreadyExistsException {

        String userID = userAccountService.create(userDetails);
        URI uri = ServletUriComponentsBuilder
                .fromCurrentRequest().path("/{id}")
                .buildAndExpand(userID).toUri();

        return ResponseEntity.created(uri).body(userID);
    }

    @RequestMapping(method = RequestMethod.PATCH, value = "/enable/{userID}")
    @ApiOperation(value = "Enable a User Account", nickname = "enable")
    public ResponseEntity<String> enable(@PathVariable String userID) {

        userAccountService.enable(userID);
        return ResponseEntity.accepted().build();
    }

    @RequestMapping(method = RequestMethod.DELETE, value = "/delete/{userID}")
    @ApiOperation(value = "Delete a User Account", nickname = "delete")
    public ResponseEntity<String> delete(@PathVariable String userID) {

        userAccountService.delete(userID);
        return ResponseEntity.noContent().build();
    }

    @RequestMapping(method = RequestMethod.GET, value = "/users")
    @ApiOperation(value = "Fetch User Accounts", nickname = "users")
    public ResponseEntity<List<AzureUser>> users() {

        List<AzureUser> azureUsers = userAccountService.getUsers();
        return ResponseEntity.ok().body(azureUsers);
    }
}
