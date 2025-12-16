package com.example.demo.existingapis.clients;

import java.math.BigDecimal;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

import com.example.demo.existingapis.models.ProductDetail;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;

@Service
public class ExistingApisClient {

  private final RestClient restClient;

  public ExistingApisClient(RestClient.Builder restClientBuilder) {
    this.restClient = restClientBuilder.baseUrl("http://localhost:3001").build();
  }

  @Cacheable(cacheNames = "similarProducts", key = "#productId", unless = "#result == null || #result.isEmpty()")
  @CircuitBreaker(name = "similarProducts", fallbackMethod = "getSimilarProductsFallback")
  public @Nullable List<String> getSimilarProducts(String productId) {
    try {
      return restClient
          .get()
          .uri("/product/{productId}/similarids", productId)
          .retrieve()
          .body(new ParameterizedTypeReference<>() {
          });
    } catch (HttpClientErrorException.NotFound _) {
      return null;
    } catch (HttpServerErrorException e) {
      throw new RuntimeException("Error fetching similar products", e);
    }
  }

  @Cacheable(cacheNames = "productDetails", key = "#productId", unless = "#result == null || #result.name() == 'Service Unavailable'")
  @CircuitBreaker(name = "productDetails", fallbackMethod = "getProductDetailsFallback")
  public @Nullable ProductDetail getProductDetails(String productId) {
    try {
      return restClient
          .get()
          .uri("/product/{productId}", productId)
          .retrieve()
          .body(ProductDetail.class);
    } catch (HttpStatusCodeException _) {
      return null;
    }
  }

  private List<String> getSimilarProductsFallback(String productId, Throwable throwable) {
    return List.of();
  }

  private ProductDetail getProductDetailsFallback(String productId, Throwable throwable) {
    return new ProductDetail(productId, "Service Unavailable", BigDecimal.ZERO, false);
  }
}
