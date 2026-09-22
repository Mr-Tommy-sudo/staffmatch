package owoke.staffmatch.backend;

import org.springframework.boot.SpringApplication;

public class TestStaffmatchApplication {

	public static void main(String[] args) {
		SpringApplication.from(StaffmatchApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
