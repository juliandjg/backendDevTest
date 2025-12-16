package com.example.demo.similarproducts.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.client.RestClient;

import com.example.demo.existingapis.clients.ExistingApisClient;
import com.example.demo.existingapis.models.ProductDetail;
import com.example.demo.similarproducts.rest.response.SimilarProducts;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "resilience4j.circuitbreaker.instances.similarProducts.failureRateThreshold=50",
        "resilience4j.circuitbreaker.instances.similarProducts.slidingWindowSize=5",
        "resilience4j.circuitbreaker.instances.similarProducts.waitDurationInOpenState=10s"
})
public class SimilarProductsControllerIntegrationTest {

    private RestTestClient restTestClient;

    @LocalServerPort
    private int port;

    @MockitoBean
    private ExistingApisClient existingApisClient;

    @BeforeEach
    void setup() {
        this.restTestClient = RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
        when(existingApisClient.getSimilarProducts("-1")).thenReturn(null);
        when(existingApisClient.getSimilarProducts("1")).thenReturn(List.of("2", "3"));
        when(existingApisClient.getSimilarProducts("2")).thenReturn(List.of());
        when(existingApisClient.getSimilarProducts("3")).thenReturn(List.of("2", "9999"));

        when(existingApisClient.getProductDetails("2")).thenReturn(
                new ProductDetail("2", "Product 2", new BigDecimal("20.00"), true));
        when(existingApisClient.getProductDetails("3")).thenReturn(
                new ProductDetail("3", "Product 3", new BigDecimal("30.00"), false));
        when(existingApisClient.getProductDetails("9999")).thenReturn(null);
    }

    @Test
    void shouldReturnNotFoundWithEmptyBodyOnInvalidProductId() {
        restTestClient.get()
                .uri("/product/-1/similar")
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectBody()
                .isEmpty();
    }

    @Test
    void shouldReturnSimilarProductsOnValidProductId() {
        var expectedProducts = new SimilarProducts(
                List.of(
                        new ProductDetail("2", "Product 2", new BigDecimal("20.00"), true),
                        new ProductDetail("3", "Product 3", new BigDecimal("30.00"), false)));

        restTestClient.get()
                .uri("/product/1/similar")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(SimilarProducts.class)
                .isEqualTo(expectedProducts);
    }

    @Test
    void shouldReturnEmptySimilarProductsWhenNoSimilarProductsExist() {
        restTestClient.get()
                .uri("/product/2/similar")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(SimilarProducts.class)
                .isEqualTo(new SimilarProducts(List.of()));
    }

    @Test
    void shouldSkipBrokenProductDetails() {
        var expectedProducts = new SimilarProducts(
                List.of(new ProductDetail("2", "Product 2", new BigDecimal("20.00"), true)));

        restTestClient.get()
                .uri("/product/3/similar")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(SimilarProducts.class)
                .isEqualTo(expectedProducts);
    }

}
