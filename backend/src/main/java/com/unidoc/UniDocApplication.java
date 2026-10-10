package com.unidoc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class UniDocApplication {

	public static void main(String[] args) {
		SpringApplication.run(UniDocApplication.class, args);
	}

}
