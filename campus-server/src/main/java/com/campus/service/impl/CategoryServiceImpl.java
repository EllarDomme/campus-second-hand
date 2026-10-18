package com.campus.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.campus.context.BaseContext;
import com.campus.dto.CategoryDTO;
import com.campus.dto.CategoryPageQueryDTO;
import com.campus.entity.Category;
import com.campus.mapper.CategoryMapper;
import com.campus.result.PageResult;
import com.campus.service.CategoryService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class CategoryServiceImpl implements CategoryService {

    @Autowired
    private CategoryMapper categoryMapper;

    /**
     * 新增分类
     */
    @Override
    public void insert(CategoryDTO categoryDTO) {
        //将DTO转换为category实体对象
        Category category = new Category();
        BeanUtils.copyProperties(categoryDTO,category);
        //设置对象成员的各个默认值，分类状态为0，创建时间，更新时间，创建人id，以及修改人id
        /**
        category.setCreateTime(LocalDateTime.now());
        category.setUpdateTime(LocalDateTime.now());
        //通过threadlocal获取当前登录用户id，设置为创建人id，以及修改人id
        category.setCreateUser(BaseContext.getCurrentId());
        category.setUpdateUser(BaseContext.getCurrentId());
         **/

        categoryMapper.insert(category);
    }

    /**
     * 分页查询分类
     */
    @Override
    public PageResult pageQuery(CategoryPageQueryDTO categoryPageQueryDTO) {
        //利用pagehelper分页查询分类
        PageHelper.startPage(categoryPageQueryDTO.getPage(),categoryPageQueryDTO.getPageSize());

        //调用mapper查询分类
        Page<Category> list = categoryMapper.selectList(categoryPageQueryDTO);
        //返回分页结果
        long total = list.getTotal();
        List<Category> categoryList = list.getResult();
        return new PageResult(total,categoryList);
    }

    /**
     * 删除分类
     */
    @Override
    public void delete(Long id) {
        categoryMapper.deleteById(id);
    }

    /**
     * 更新分类
     */
    @Override
    public void update(CategoryDTO categoryDTO) {
        Category category = new Category();
        BeanUtils.copyProperties(categoryDTO,category);
        /**
        //设置更新时间
        category.setUpdateTime(LocalDateTime.now());
        //设置修改人id
        category.setUpdateUser(BaseContext.getCurrentId());
         **/

        categoryMapper.update(category);
    }

    /**
     * 查询分类
     * @param
     * @return
     */
    public List<Category> list() {
        List<Category> categoryList = categoryMapper.selectAll();
        return categoryList;
    }
}
