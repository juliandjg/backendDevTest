package com.example.demo.similarproducts.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.test.autoconfigure.AutoConfigureMockRestServiceServer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.test.web.servlet.client.RestTestClient;

import com.example.demo.existingapis.models.ProductDetail;
import com.example.demo.similarproducts.rest.response.SimilarProducts;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
        "resilience4j.circuitbreaker.instances.similarProducts.sliding-window-size=5",
        "resilience4j.circuitbreaker.instances.similarProducts.failure-rate-threshold=50",
        "resilience4j.circuitbreaker.instances.similarProducts.wait-duration-in-open-state=10s",
        "resilience4j.circuitbreaker.instances.similarProducts.max-wait-duration-in-half-open-state=10s",
        "resilience4j.circuitbreaker.instances.similarProducts.permitted-number-of-calls-in-half-open-state=3",
        "resilience4j.circuitbreaker.instances.productDetails.sliding-window-size=5",
        "resilience4j.circuitbreaker.instances.productDetails.failure-rate-threshold=50",
        "resilience4j.circuitbreaker.instances.productDetails.wait-duration-in-open-state=10s",
        "resilience4j.circuitbreaker.instances.productDetails.max-wait-duration-in-half-open-state=10s",
        "resilience4j.circuitbreaker.instances.productDetails.permitted-number-of-calls-in-half-open-state=3",
})
@AutoConfigureMockRestServiceServer
class SimilarProductsCacheCBIntegrationTest {

    @Autowired
    private MockRestServiceServer mockServer;

    @Autowired
    private CircuitBreakerRegistry registry;

    @LocalServerPort
    private int port;

    private RestTestClient restTestClient;

    @BeforeEach
    void setup() {

        this.restTestClient = RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @Test
    void shouldReturnCachedSimilarProductsOnRepeatedRequests() {
        mockServer.expect(MockRestRequestMatchers.requestTo("http://localhost:3001/product/1/similarids"))
                .andRespond(MockRestResponseCreators.withSuccess("[\"2\",\"3\"]", MediaType.APPLICATION_JSON));

        mockServer.expect(MockRestRequestMatchers.requestTo("http://localhost:3001/product/2"))
                .andRespond(MockRestResponseCreators.withSuccess(
                        "{\"id\":\"2\",\"name\":\"Product 2\",\"price\":20.00,\"availability\":true}",
                        MediaType.APPLICATION_JSON));

        mockServer.expect(MockRestRequestMatchers.requestTo("http://localhost:3001/product/3"))
                .andRespond(MockRestResponseCreators.withSuccess(
                        "{\"id\":\"3\",\"name\":\"Product 3\",\"price\":30.00,\"availability\":false}",
                        MediaType.APPLICATION_JSON));

        var expectedProducts = new SimilarProducts(List.of(
                new ProductDetail("2", "Product 2", new BigDecimal("20.00"), true),
                new ProductDetail("3", "Product 3", new BigDecimal("30.00"), false)));

        for (int i = 0; i < 3; i++) {
            restTestClient.get()
                    .uri("/product/1/similar")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(SimilarProducts.class)
                    .isEqualTo(expectedProducts);
        }

        mockServer.verify();
    }

    @Disabled("Support for CB resilience4j with spring boot 4 integration WIP change to branch explicit-cb to see it working")
    @Test
    void shouldHandleCircuitBreakerOpenStateGracefully() {
        mockServer.expect(ExpectedCount.manyTimes(),
                MockRestRequestMatchers.requestTo("http://localhost:3001/product/9999/similarids"))
                .andRespond(MockRestResponseCreators.withServerError());

        for (int i = 0; i < 4; i++) {
            restTestClient.get()
                    .uri("/product/9999/similar")
                    .exchange()
                    .expectStatus().is5xxServerError();
        }

        assertEquals(CircuitBreaker.State.OPEN,
                registry.circuitBreaker("similarProducts").getState());
    }
}
