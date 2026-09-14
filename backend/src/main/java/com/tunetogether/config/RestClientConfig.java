package com.tunetogether.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * A single shared, pooled outbound HTTP client for calling YouTube and Google's
 * tokeninfo endpoint. Backed by the JDK's built-in {@link HttpClient} (HTTP/2,
 * automatic keep-alive connection pooling per host) rather than pulling in Apache
 * HttpClient5 - it needs no extra dependency and gives the same practical benefit
 * (reused pooled connections instead of one-shot sockets per request) that
 * RestTemplate's default factory never provided.
 */
@Configuration
public class RestClientConfig {

    @Bean
    public HttpClient jdkHttpClient() {
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Bean
    public ClientHttpRequestFactory clientHttpRequestFactory(HttpClient jdkHttpClient) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(jdkHttpClient);
        factory.setReadTimeout(Duration.ofSeconds(10));
        return factory;
    }

    @Bean
    public RestClient restClient(ClientHttpRequestFactory clientHttpRequestFactory) {
        return RestClient.builder()
                .requestFactory(clientHttpRequestFactory)
                .build();
    }
}
