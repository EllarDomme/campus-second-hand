package com.sky.controller.user;

import com.sky.result.Result;
import com.sky.service.FavoriteService;
import com.sky.vo.ProductVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * C端-收藏接口
 *
 * 这个文件是在你写的基础上改的，你对照着看改了哪几处：
 *   ① 补上 Result 的 import
 *   ② Result<void> 改成 Result（Java 泛型不能写 void）
 *   ③ 方法参数改成 @RequestParam Long productId —— 前端点收藏只知道"哪件商品"，
 *      用户是谁由后端从登录态里取，不用前端传
 *   ④ 注入 FavoriteService 并调用（Controller 只做转发，业务逻辑在 Service）
 *   ⑤ 补上返回值和方法/类的结束大括号
 *   ⑥ Bean 名改成 userFavoriteController，和项目里 userProductController 风格一致
 */
@RestController("userFavoriteController")
@RequestMapping("/user/favorite")
@Api(tags = "C端-收藏接口")
@Slf4j
public class FavoriteController {

    @Autowired
    private FavoriteService favoriteService;

    /**
     * 收藏商品
     * 用 @RequestParam 表示参数来自 URL 查询串：POST /user/favorite?productId=1
     */
    @PostMapping
    @ApiOperation("收藏一个商品")
    public Result favorite(@RequestParam Long productId) {
        log.info("收藏商品：{}", productId);
        favoriteService.addFavorite(productId);
        return Result.success();
    }

    /**
     * 取消收藏
     */
    @DeleteMapping
    @ApiOperation("取消收藏")
    public Result remove(@RequestParam Long productId) {
        log.info("取消收藏：{}", productId);
        favoriteService.removeFavorite(productId);
        return Result.success();
    }

    /**
     * 我的收藏列表
     * 返回商品信息（含 status），前端据此显示"已售罄"并禁止下单
     */
    @GetMapping("/list")
    @ApiOperation("我的收藏列表")
    public Result<List<ProductVO>> list() {
        log.info("查询我的收藏列表");
        return Result.success(favoriteService.listFavorite());
    }
}
