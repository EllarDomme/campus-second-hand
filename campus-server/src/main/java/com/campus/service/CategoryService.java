package com.campus.service;

import com.campus.dto.CategoryDTO;
import com.campus.dto.CategoryPageQueryDTO;
import com.campus.entity.Category;
import com.campus.result.PageResult;

import java.util.List;

public interface CategoryService {
    void insert(CategoryDTO categoryDTO);

    PageResult pageQuery(CategoryPageQueryDTO categoryPageQueryDTO);

    void delete(Long id);

    void update(CategoryDTO categoryDTO);

    List<Category> list();
}
