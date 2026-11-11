package io.github.yazar.portfolio.config;

import java.net.http.HttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(EuropePmcProperties.class)
public class EuropePmcClientConfig {

    /**
     * One HTTP client for both Europe PMC APIs. The timeouts are explicit so that a slow or unresponsive
     * upstream fails the call instead of holding a request thread indefinitely.
     */
    @Bean
    RestClient europePmcRestClient(RestClient.Builder builder, EuropePmcProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                // Europe PMC's servers drop the JDK client's HTTP/2 connections ("EOF reached while reading"),
                // while HTTP/1.1 works. At a handful of concurrent requests, HTTP/2 would gain nothing anyway.
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());

        return builder
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.USER_AGENT, "institution-portfolio")
                .build();
    }
}
