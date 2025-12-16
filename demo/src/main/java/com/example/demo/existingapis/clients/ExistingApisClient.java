package com.example.demo.existingapis.clients;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

import com.example.demo.existingapis.models.ProductDetail;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

@Service
public class ExistingApisClient {

  private final RestClient restClient;
  private final CircuitBreaker similarProductsCB;
  private final CircuitBreaker productDetailsCB;

  public ExistingApisClient(
    RestClient.Builder restClientBuilder,
    CircuitBreakerRegistry registry
  ) {
    this.restClient = restClientBuilder
      .baseUrl("http://localhost:3001")
      .build();
    this.similarProductsCB = registry.circuitBreaker("similarProducts");
    this.productDetailsCB = registry.circuitBreaker("productDetails");
  }

  @Cacheable(cacheNames = "similarProducts", key = "#productId", unless = "#result == null || #result.isEmpty()")
  public @Nullable List<String> getSimilarProducts(String productId) {
    Supplier<List<String>> supplier = () -> {
      try {
        return restClient
          .get()
          .uri("/product/{productId}/similarids", productId)
          .retrieve()
          .body(new ParameterizedTypeReference<List<String>>() {});
      } catch (HttpClientErrorException.NotFound _) {
        return null;
      } catch (HttpServerErrorException e) {
        throw new RuntimeException("Error fetching similar products", e);
      }
    };

    return cbWithFallback(similarProductsCB.decorateSupplier(supplier), List.of());
  }

  @Cacheable(cacheNames = "productDetails", key = "#productId", unless = "#result == null || #result.name() == 'Service Unavailable'")
  public @Nullable ProductDetail getProductDetails(String productId) {
    Supplier<ProductDetail> supplier = () -> {
      try {
        return restClient
          .get()
          .uri("/product/{productId}", productId)
          .retrieve()
          .body(ProductDetail.class);
      } catch (HttpStatusCodeException _) {
        return null;
      }
    };

    return cbWithFallback(
      productDetailsCB.decorateSupplier(supplier),
      new ProductDetail(productId, "Service Unavailable", BigDecimal.ZERO, false)
    );
  }

  private <T> T cbWithFallback(Supplier<T> decoratedSupplier, T fallback) {
    try {
      return decoratedSupplier.get();
    } catch (CallNotPermittedException _) {
      return fallback;
    }
  }
}
