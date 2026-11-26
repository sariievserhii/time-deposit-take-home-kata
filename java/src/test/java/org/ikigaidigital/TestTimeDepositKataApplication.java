package org.ikigaidigital;

import org.springframework.boot.SpringApplication;

/**
 * Local run with PostgreSQL in Testcontainers (mvn spring-boot:test-run). Activates the "dev" profile for the demo
 * API users. The dev datasource settings are ignored, because the Testcontainers @ServiceConnection takes precedence.
 */
public class TestTimeDepositKataApplication {

	public static void main(String[] args) {
		SpringApplication.from(TimeDepositKataApplication::main)
				.with(TestcontainersConfiguration.class)
				.withAdditionalProfiles("dev")
				.run(args);
	}

}
