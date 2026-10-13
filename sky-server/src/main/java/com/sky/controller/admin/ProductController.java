package com.sky.controller.admin;

import com.sky.dto.ProductPageQueryDTO;
import com.sky.result.Result;
import com.sky.service.ProductService;
import com.sky.vo.ProductVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

/**
 * 管理端-商品管理与审核
 *
 * 职责边界：商品的"发布/编辑/上下架"属于卖家（用户端），管理端只做
 *   ① 分页查看（含审核列表）② 审核通过/驳回 ③ 删除违规商品
 */
@RestController
@RequestMapping("/admin/product")
@Api(tags = "管理端-商品管理与审核")
@Slf4j
public class ProductController {

    @Autowired
    private ProductService productService;

    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * 商品分页查询
     * 审核列表用法：auditStatus=0 查待审核；不传则查全部
     */
    @GetMapping("/page")
    @ApiOperation("商品分页查询（审核列表）")
    public Result page(ProductPageQueryDTO productPageQueryDTO) {
        log.info("管理端分页查询商品：{}", productPageQueryDTO);
        return Result.success(productService.page(productPageQueryDTO));
    }

    /**
     * 商品详情（审核时查看内容与图片）
     */
    @GetMapping("/{id}")
    @ApiOperation("查看商品详情")
    public Result<ProductVO> getById(@PathVariable Long id) {
        log.info("管理端查看商品详情：{}", id);
        return Result.success(productService.getById(id));
    }

    /**
     * 审核商品
     *
     * @param auditStatus 1通过 2驳回（驳回必须填 auditNote）
     */
    @PostMapping("/audit")
    @ApiOperation("审核商品：1通过 2驳回")
    public Result audit(@RequestParam Long id,
                        @RequestParam Integer auditStatus,
                        @RequestParam(required = false) String auditNote) {
        log.info("审核商品：id={}, auditStatus={}, auditNote={}", id, auditStatus, auditNote);
        productService.audit(id, auditStatus, auditNote);
        // 审核结果直接决定用户端能看到哪些商品，必须清掉浏览缓存
        cleanCache();
        return Result.success();
    }

    /**
     * 删除违规商品（批量）
     */
    @DeleteMapping
    @ApiOperation("删除违规商品")
    public Result delete(@RequestParam List<Long> ids) {
        log.info("管理员删除商品：{}", ids);
        productService.deleteByIds(ids);
        cleanCache();
        return Result.success();
    }

    /**
     * 清空商品浏览缓存
     * 注意：这里的 pattern 必须和用户端 ProductController 里的 CACHE_KEY_PREFIX 保持一致，
     * 两边改一个不改另一个，缓存就不会被清掉（表现为"改了商品但列表没变"）
     */
    private void cleanCache() {
        Set keys = redisTemplate.keys("product_list_*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }
}
