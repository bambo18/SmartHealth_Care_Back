package com.smarthealthdog.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    /**
     * Defines a WebClient bean using the auto-configured WebClient.Builder.
     * This bean can be injected into any service/component that needs to make
     * non-blocking HTTP requests, such as your OAuthClient.
     */
    @Bean
    public WebClient webClient(WebClient.Builder builder) {
        // You can use the builder to set a base URL, default headers, 
        // timeouts, or other properties common to your social login APIs.
        
        return builder.build();
    }

    /**
     * Alternatively, if you need different WebClient instances 
     * for different services, you can define multiple beans:
     * * @Bean
     * public WebClient googleOAuthClient(WebClient.Builder builder) {
     * return builder.baseUrl("https://oauth2.googleapis.com").build();
     * }
     */
}