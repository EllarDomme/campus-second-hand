package com.sky.controller.admin;

import com.sky.dto.CategoryDTO;
import com.sky.dto.CategoryPageQueryDTO;
import com.sky.entity.Category;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.CategoryService;
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
     * 更新分类状态
     * @param status
     * @param id
     * @return
     */
    @PostMapping("/status/{status}")
    @ApiOperation("更新分类状态")
    public Result updateStatus(@PathVariable Integer status, @RequestParam Long id) {
        log.info("更新分类状态：{},分类id：{}",status,id);
        categoryService.updateStatus(status, id);
        return Result.success();
    }

    /**
     * 根据类型查询分类
     * @param categoryDTO
     * @return
     */
    @GetMapping("/list")
    @ApiOperation("根据类型查询分类")
    public Result getCategoryList(CategoryDTO categoryDTO) {
        log.info("根据类型查询分类：{}",categoryDTO);
        List<Category> categoryList = categoryService.listByType(categoryDTO);
        return Result.success(categoryList);
    }
}
