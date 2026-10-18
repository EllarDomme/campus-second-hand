package com.campus.controller.user;

import com.campus.dto.ProductDTO;
import com.campus.dto.ProductPageQueryDTO;
import com.campus.result.Result;
import com.campus.service.ProductService;
import com.campus.vo.ProductVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * C端-商品接口（卖家发布 / 买家浏览）
 */
@RestController("userProductController")
@RequestMapping("/user/product")
@Api(tags = "C端-商品接口")
@Slf4j
public class ProductController {

    /**
     * 商品浏览缓存的 key 前缀。
     * ⚠️ 必须与管理端 ProductController 里 cleanCache("product_list_*") 保持一致，
     * 否则会出现"商品已改但列表还是旧的"。
     */
    private static final String CACHE_KEY_PREFIX = "product_list_";

    /**
     * 缓存过期时间（分钟）。
     * 管理员审核 / 卖家编辑时会主动清缓存，这里再加一层兜底，
     * 避免任何遗漏导致缓存永久不更新。
     */
    private static final long CACHE_TTL_MINUTES = 30;

    @Autowired
    private ProductService productService;

    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * 按分类浏览商品（只返回审核通过 + 在售的）
     */
    @GetMapping("/list")
    @ApiOperation("按分类浏览在售商品")
    public Result<List<ProductVO>> list(Long categoryId) {
        String key = CACHE_KEY_PREFIX + categoryId;

        List<ProductVO> cached = (List<ProductVO>) redisTemplate.opsForValue().get(key);
        if (cached != null) {
            log.info("商品列表命中缓存：{}", key);
            return Result.success(cached);
        }

        List<ProductVO> list = productService.listByCategoryId(categoryId);
        redisTemplate.opsForValue().set(key, list, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        return Result.success(list);
    }

    /**
     * 商品详情
     */
    @GetMapping("/{id}")
    @ApiOperation("商品详情")
    public Result<ProductVO> getById(@PathVariable Long id) {
        return Result.success(productService.getById(id));
    }

    /**
     * 我发布的商品（分页）
     */
    @GetMapping("/mine")
    @ApiOperation("我发布的商品")
    public Result mine(ProductPageQueryDTO productPageQueryDTO) {
        log.info("查询我发布的商品：{}", productPageQueryDTO);
        return Result.success(productService.pageMine(productPageQueryDTO));
    }

    /**
     * 发布商品（发布后进入待审核，管理员审核通过才对外展示）
     */
    @PostMapping
    @ApiOperation("发布商品")
    public Result save(@RequestBody ProductDTO productDTO) {
        log.info("发布商品：{}", productDTO);
        productService.save(productDTO);
        cleanCache();
        return Result.success();
    }

    /**
     * 编辑商品（编辑后重新回到待审核）
     */
    @PutMapping
    @ApiOperation("编辑商品")
    public Result update(@RequestBody ProductDTO productDTO) {
        log.info("编辑商品：{}", productDTO);
        productService.update(productDTO);
        cleanCache();
        return Result.success();
    }

    /**
     * 商品上下架：0在售 3已下架
     */
    @PostMapping("/status/{status}")
    @ApiOperation("商品上下架：0在售 3已下架")
    public Result updateStatus(@PathVariable Integer status, @RequestParam Long id) {
        log.info("商品上下架：id={}, status={}", id, status);
        productService.updateStatus(status, id);
        cleanCache();
        return Result.success();
    }

    /**
     * 删除商品
     */
    @DeleteMapping
    @ApiOperation("删除商品")
    public Result delete(@RequestParam Long id) {
        log.info("删除商品：{}", id);
        productService.deleteBySeller(id);
        cleanCache();
        return Result.success();
    }

    // ==========================================================================
    // 【公共逻辑 · 清商品缓存】本类里被 4 处调用（发布/编辑/上下架/删除）
    //   ⚠️ 跨文件耦合：缓存 key 前缀必须和管理端 admin/ProductController.cleanCache()
    //      保持一致（都是 product_list_）。两边改一个不改另一个 →
    //      缓存清不掉 → 现象是"商品改了但列表没变"，且不报错，很难查。
    // ==========================================================================
    /**
     * 清空商品浏览缓存
     */
    private void cleanCache() {
        Set keys = redisTemplate.keys(CACHE_KEY_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }
}
