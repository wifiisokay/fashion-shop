package com.fashionshop.backend.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.fashionshop.backend.domain.Product;

public interface ProductRepository extends JpaRepository<Product, Long>,
                                           JpaSpecificationExecutor<Product> {

    boolean existsByCategoryId(Integer categoryId);

    long countByStatus(com.fashionshop.backend.common.enums.ProductStatus status);

}
