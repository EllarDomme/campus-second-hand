package com.campus.controller.admin;

import com.campus.dto.CategoryDTO;
import com.campus.dto.CategoryPageQueryDTO;
import com.campus.entity.Category;
import com.campus.result.PageResult;
import com.campus.result.Result;
import com.campus.service.CategoryService;
import io.swagger.annotations.ApiOperation;
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
    @ApiOperation("添加分类")
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
    @ApiOperation("分页查询分类")
    public Result pageQuery(CategoryPageQueryDTO categoryPageQueryDTO) {
        log.info("分页查询分类：{}",categoryPageQueryDTO);
        PageResult pageResult = categoryService.pageQuery(categoryPageQueryDTO);
        return Result.success(pageResult);
    }

    /**
     * 删除分类
     * @param id
     * @return
     */
    @DeleteMapping()
    @ApiOperation("删除分类")
    public Result deleteCategory(@RequestParam Long id) {
        log.info("删除分类id：{}",id);
        categoryService.delete(id);
        return Result.success();
    }

    /**
     * 更新分类
     * @param categoryDTO
     * @return
     */
    @PutMapping()
    @ApiOperation("更新分类")
    public Result updateCategory(@RequestBody CategoryDTO categoryDTO) {
        log.info("更新分类：{}",categoryDTO);
        categoryService.update(categoryDTO);
        return Result.success();
    }

    /**
     * 查询分类
     * @return
     */
    @GetMapping("/list")
    @ApiOperation("查询分类")
    public Result getCategoryList() {
        log.info("查询分类");
        List<Category> categoryList = categoryService.list();
        return Result.success(categoryList);
    }
}
