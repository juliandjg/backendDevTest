package com.example.demo.existingapis.models;

import java.math.BigDecimal;

public record ProductDetail(
  String id,
  String name,
  BigDecimal price,
  Boolean availability
) {}
