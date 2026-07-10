package com.sky.controller.admin;

import com.sky.dto.CategoryDTO;
import com.sky.dto.CategoryPageQueryDTO;
import com.sky.entity.Category;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.CategoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Slf4j
@RequestMapping("/admin/category")
public class CategoryController {

    @Autowired
    private CategoryService categoryService;

    /**
     * 添加分类
     * @param categoryDTO
     * @return
     */
    @PostMapping()
    public Result addCategory(@RequestBody CategoryDTO categoryDTO) {
        log.info("添加分类：{}",categoryDTO);
        categoryService.insert(categoryDTO);
        return Result.success();
    }

    /**
     * 分页查询分类
     * @param categoryPageQueryDTO
     * @return
     */
    @GetMapping("/page")
    public Result pageQuery(CategoryPageQueryDTO categoryPageQueryDTO) {
        log.info("分页查询分类：{}",categoryPageQueryDTO);
        PageResult pageResult = categoryService.pageQuery(categoryPageQueryDTO);
        return Result.success(pageResult);
    }

    /**
     * 删除分类
     * @param categoryDTO
     * @return
     */
    @DeleteMapping()
    public Result deleteCategory(@RequestBody CategoryDTO categoryDTO) {
        log.info("删除分类：{}",categoryDTO);
        categoryService.delete(categoryDTO.getId());
        return Result.success();
    }

    /**
     * 更新分类
     * @param categoryDTO
     * @return
     */
    @PutMapping()
    public Result updateCategory(@RequestBody CategoryDTO categoryDTO) {
        log.info("更新分类：{}",categoryDTO);
        categoryService.update(categoryDTO);
        return Result.success();
    }

    /**
     * 更新分类状态
     * @param status
     * @param categoryDTO
     * @return
     */
    @PostMapping("/status/{status}")
    public Result updateStatus(@PathVariable Integer status, @RequestBody CategoryDTO categoryDTO) {
        log.info("更新分类状态：{}",status);
        categoryService.updateStatus(status, categoryDTO.getId());
        return Result.success();
    }

    /**
     * 根据类型查询分类
     * @param categoryDTO
     * @return
     */
    @GetMapping("/list")
    public Result getCategoryList(CategoryDTO categoryDTO) {
        log.info("根据类型查询分类：{}",categoryDTO);
        Category category = new Category();
        category = categoryService.listByType(categoryDTO);
        return Result.success(category);
    }
}
