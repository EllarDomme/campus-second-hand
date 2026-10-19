package com.campus.controller.user;

import com.campus.result.Result;
import com.campus.utils.LocalStorageUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 用户端-图片上传
 *
 * 为什么挂在 /user/** 下而不是复用 /admin/common/upload：
 *   ① 那一个是管理员专属（要 token 请求头），普通用户调不了
 *   ② 那一个走阿里云 OSS，本地离线环境必然失败
 * 这里存本地磁盘，由 /upload/** 提供访问。
 */
@RestController("userUploadController")
@RequestMapping("/user/upload")
@Api(tags = "用户端-图片上传")
@Slf4j
public class UploadController {

    @Autowired
    private LocalStorageUtil storageUtil;

    /**
     * 上传一张图片
     * 用法：multipart/form-data，字段名 file
     *
     * @return 图片的访问路径，例如 /upload/2026/10/xxxx.png
     */
    @PostMapping
    @ApiOperation("上传图片（返回可直接访问的路径）")
    public Result<String> upload(MultipartFile file) {
        String url = storageUtil.save(file);
        return Result.success(url);
    }

}
