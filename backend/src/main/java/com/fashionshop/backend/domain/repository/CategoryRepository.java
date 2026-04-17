package com.fashionshop.backend.domain.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fashionshop.backend.domain.Category;

public interface CategoryRepository extends JpaRepository<Category, Integer> {

    Optional<Category> findBySlug(String slug);

    boolean existsBySlug(String slug);

    List<Category> findByParentIsNullOrderByNameAsc();

    List<Category> findByParentIdOrderByNameAsc(Integer parentId);
}
