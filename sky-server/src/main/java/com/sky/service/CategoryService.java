package com.sky.service;

import com.sky.dto.CategoryDTO;
import com.sky.dto.CategoryPageQueryDTO;
import com.sky.entity.Category;
import com.sky.result.PageResult;

import java.util.List;

public interface CategoryService {
    void insert(CategoryDTO categoryDTO);

    PageResult pageQuery(CategoryPageQueryDTO categoryPageQueryDTO);

    void delete(Long id);

    void update(CategoryDTO categoryDTO);

    void updateStatus(Integer status, Long id);

    List<Category> listByType(CategoryDTO categoryDTO);
}
