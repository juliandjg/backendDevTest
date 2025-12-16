package com.example.demo.similarproducts.rest;

import java.util.Objects;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.existingapis.clients.ExistingApisClient;
import com.example.demo.similarproducts.rest.response.SimilarProducts;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@RestController
@RequestMapping("/product")
public class SimilarProductsController {

  private final ExistingApisClient existingApisClient;

  @GetMapping("/{productId}/similar")
  public ResponseEntity<SimilarProducts> getSimilarProducts(
    @PathVariable String productId
  ) {
    var similarProductIdsResponse = existingApisClient.getSimilarProducts(
      productId
    );

    if (similarProductIdsResponse == null) {
      return ResponseEntity.notFound().build();
    }

    var similarProductDetails = similarProductIdsResponse
      .stream()
      .map(existingApisClient::getProductDetails)
      .filter(Objects::nonNull)
      .toList();

    return ResponseEntity.ok(new SimilarProducts(similarProductDetails));
  }
}
