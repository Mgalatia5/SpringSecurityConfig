package com.mgalatia.springsecurity;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

@EnableDiscoveryClient
@SpringBootApplication
@RequiredArgsConstructor
public class SpringSecurityPracticeApplication {
private final UserService userService;
	public static void main(String[] args) {
		SpringApplication.run(SpringSecurityPracticeApplication.class, args);
	}


	@Bean
	CommandLineRunner runner() {
		return args -> {
			User user = new User(1L, "admin@gmail.com","1423");
			userService.create(user);
		};
	}

}

