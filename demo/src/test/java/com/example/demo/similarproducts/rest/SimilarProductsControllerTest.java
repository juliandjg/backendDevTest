package com.example.demo.similarproducts.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.example.demo.existingapis.clients.ExistingApisClient;
import com.example.demo.existingapis.models.ProductDetail;
import com.example.demo.similarproducts.rest.response.SimilarProducts;

@ExtendWith(MockitoExtension.class)
public class SimilarProductsControllerTest {

    @Mock
    private ExistingApisClient existingApisClient;

    @InjectMocks
    private SimilarProductsController similarProductsController;

    @Test
    void shouldReturnNotFoundOnInvalidProductId() {
        when(existingApisClient.getSimilarProducts("-1")).thenReturn(null);

        ResponseEntity<SimilarProducts> similarProducts = similarProductsController.getSimilarProducts("-1");

        assertTrue(similarProducts.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND));
    }

    @Test
    void shouldReturnSimilarProductsOnValidProductId() {
        when(existingApisClient.getSimilarProducts("1")).thenReturn(java.util.List.of("2", "3"));
        when(existingApisClient.getProductDetails("2")).thenReturn(
                new ProductDetail("2", "Product 2", new BigDecimal("20.00"), true));
        when(existingApisClient.getProductDetails("3")).thenReturn(
                new ProductDetail("3", "Product 3", new BigDecimal("30.00"), false));
        var expectedProducts = new SimilarProducts(
                List.of(
                        new ProductDetail("2", "Product 2", new BigDecimal("20.00"), true),
                        new ProductDetail("3", "Product 3", new BigDecimal("30.00"), false)));

        ResponseEntity<SimilarProducts> similarProducts = similarProductsController.getSimilarProducts("1");

        assertEquals(expectedProducts, similarProducts.getBody());
    }

    @Test
    void shouldReturnEmptySimilarProductsWhenNoSimilarProductsExist() {
        when(existingApisClient.getSimilarProducts("4")).thenReturn(List.of());
        var expectedProducts = new SimilarProducts(List.of());

        ResponseEntity<SimilarProducts> similarProducts = similarProductsController.getSimilarProducts("4");

        assertEquals(expectedProducts, similarProducts.getBody());
    }

    @Test
    void shouldSkipBrokenProductDetails() {
        when(existingApisClient.getSimilarProducts("5")).thenReturn(List.of("6", "7"));
        when(existingApisClient.getProductDetails("6")).thenReturn(
                new ProductDetail("6", "Product 6", new BigDecimal("60.00"), true));
        when(existingApisClient.getProductDetails("7")).thenReturn(null);
        var expectedProducts = new SimilarProducts(
                List.of(
                        new ProductDetail("6", "Product 6", new BigDecimal("60.00"), true)));

        ResponseEntity<SimilarProducts> similarProducts = similarProductsController.getSimilarProducts("5");

        assertEquals(expectedProducts, similarProducts.getBody());
    }
}
