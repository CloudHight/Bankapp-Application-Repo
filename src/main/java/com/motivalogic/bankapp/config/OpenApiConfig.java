package com.motivalogic.bankapp.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI bankappOpenAPI(@Value("${spring.application.name:bankapp}") String appName) {
        return new OpenAPI().info(new Info()
                .title(appName + " API")
                .description("REST banking API: customers, accounts and transactions")
                .version("v1")
                .contact(new Contact().name("MotivaLogic").email("support@motivalogic.com"))
                .license(new License().name("Proprietary")));
    }
}
