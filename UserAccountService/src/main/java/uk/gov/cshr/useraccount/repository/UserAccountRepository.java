package uk.gov.cshr.useraccount.repository;

import javax.transaction.Transactional;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;
import uk.gov.cshr.useraccount.model.UserAccount;

@Repository
@Transactional
public interface UserAccountRepository extends PagingAndSortingRepository<UserAccount, Long> {

    public UserAccount findByEmail(String email);

    public UserAccount findByUsername(String username);

    public UserAccount findByUserid(String userID);

    public UserAccount findByUseridStartsWith(String userIDPrefix);
}
