package org.ikigaidigital;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Placed in the root package so component, entity and repository scanning cover every layer. */
@SpringBootApplication
public class TimeDepositKataApplication {

	public static void main(String[] args) {
		SpringApplication.run(TimeDepositKataApplication.class, args);
	}

}
