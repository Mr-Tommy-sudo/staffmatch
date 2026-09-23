package owoke.staffmatch.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class StaffmatchApplication {

	public static void main(String[] args) {
		SpringApplication.run(StaffmatchApplication.class, args);
	}

}
