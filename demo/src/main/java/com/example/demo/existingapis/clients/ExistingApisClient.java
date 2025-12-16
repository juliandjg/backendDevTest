package com.example.demo.existingapis.clients;

import com.example.demo.existingapis.models.ProductDetail;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

@Service
public class ExistingApisClient {

  private final RestClient restClient;
  private final CircuitBreaker similarProductsCB;
  private final CircuitBreaker productDetailsCB;
  private final CacheManager cacheManager;

  public ExistingApisClient(
      RestClient.Builder restClientBuilder,
      CircuitBreakerRegistry registry,
      CacheManager cacheManager) {
    this.restClient = restClientBuilder
        .baseUrl("http://localhost:3001")
        .build();
    this.similarProductsCB = registry.circuitBreaker("similarProducts");
    this.productDetailsCB = registry.circuitBreaker("productDetails");
    this.cacheManager = cacheManager;
  }

  public @Nullable List<String> getSimilarProducts(String productId) {
    Supplier<List<String>> supplier = () -> {
      try {
        return restClient
            .get()
            .uri("/product/{productId}/similarids", productId)
            .retrieve()
            .body(new ParameterizedTypeReference<List<String>>() {
            });
      } catch (HttpClientErrorException.NotFound _) {
        return null;
      } catch (HttpServerErrorException e) {
        throw new RuntimeException("Error fetching similar products", e);
      }
    };

    Supplier<List<String>> decoratedSupplier = CircuitBreaker.decorateSupplier(
        similarProductsCB,
        supplier);

    var calledFromCache = fetchWithCache(
        "similarProducts",
        productId,
        decoratedSupplier);

    return cbWithFallback(calledFromCache, List.of());
  }

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

    Supplier<ProductDetail> decoratedSupplier = CircuitBreaker.decorateSupplier(
        productDetailsCB,
        supplier);

    var calledFromCache = fetchWithCache("productDetails", productId, decoratedSupplier);

    return cbWithFallback(
        calledFromCache,
        new ProductDetail(
            productId,
            "Service Unavailable",
            BigDecimal.ZERO,
            false));
  }

  private <T, K> Supplier<T> fetchWithCache(
      String cacheName,
      K key,
      Supplier<T> fetcher) {
    return fetchWithCache(cacheName, key, fetcher, Objects::nonNull);
  }

  private <T, K> Supplier<T> fetchWithCache(
      String cacheName,
      K key,
      Supplier<T> fetcher,
      Predicate<T> shouldCache) {
    Cache cache = cacheManager.getCache(cacheName);
    if (cache != null) {
      T cached = cache.get(key, (Class<T>) Object.class);
      if (cached != null) {
        return () -> cached;
      }
    }

    T result = fetcher.get();

    if (cache != null && shouldCache.test(result)) {
      cache.put(key, result);
    }

    return () -> result;
  }

  private <T> T cbWithFallback(Supplier<T> decoratedSupplier, T fallback) {
    try {
      return decoratedSupplier.get();
    } catch (CallNotPermittedException _) {
      return fallback;
    }
  }
}
