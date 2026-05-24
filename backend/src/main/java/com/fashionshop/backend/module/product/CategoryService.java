package com.fashionshop.backend.module.product;

import java.util.List;

import com.fashionshop.backend.module.product.dto.request.CategoryRequest;
import com.fashionshop.backend.module.product.dto.response.CategoryResponse;
import com.fashionshop.backend.module.product.dto.response.CategoryTreeResponse;

public interface CategoryService {

    List<CategoryTreeResponse> getTree();

    CategoryResponse getById(Integer id);

    CategoryResponse create(CategoryRequest request);

    CategoryResponse update(Integer id, CategoryRequest request);

    void delete(Integer id);
}
