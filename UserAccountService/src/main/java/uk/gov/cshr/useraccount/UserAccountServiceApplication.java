package uk.gov.cshr.useraccount;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class UserAccountServiceApplication {

    private static final Logger log = LoggerFactory.getLogger(UserAccountServiceApplication.class);

    public static void main(String[] args) {
        log.debug("UserAccountServiceApplication starting");
        SpringApplication.run(UserAccountServiceApplication.class, args);
    }
}
