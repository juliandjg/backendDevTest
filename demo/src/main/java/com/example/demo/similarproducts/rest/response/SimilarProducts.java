package com.example.demo.similarproducts.rest.response;

import com.example.demo.existingapis.models.ProductDetail;
import java.util.List;

public record SimilarProducts(List<ProductDetail> products) {}
