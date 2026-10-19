package com.campus.utils;

import com.campus.exception.BaseException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 本地图片存储（不依赖阿里云 OSS）
 *
 * ==========================================================================
 * 为什么不用项目里现成的 AliOssUtil：
 *   ① 它需要真实的 AccessKey + 公网，本地/离线环境直接失败
 *   ② 它挂在 /admin/common/upload 下，用户端拿不到管理员令牌
 * 所以给 C 端单独做一套：文件落到本地磁盘，由 Spring MVC 的静态资源映射
 * 以 /upload/** 的形式对外提供访问。换到生产环境时，只要把 save() 换成 OSS 上传，
 * 接口和 URL 形态都不用动。
 * ==========================================================================
 *
 * 安全上做了四件事（上传是典型的"用户可控输入"，最容易出事）：
 *   1) 大小限制 5MB —— 防止有人拿它当网盘
 *   2) 类型白名单 —— 只允许常见图片后缀，且 Content-Type 必须以 image/ 开头
 *   3) 文件名重写 —— 用 UUID，绝不使用用户传来的文件名
 *      （否则可能被塞进 ../../ 之类的路径穿越）
 *   4) 按年月分目录 —— 单个目录文件太多时 ls / 同步都会变慢
 */
@Component
@Slf4j
public class LocalStorageUtil {

    /** 单文件大小上限：5MB */
    private static final long MAX_SIZE = 5L * 1024 * 1024;

    /** 允许的扩展名（小写） */
    private static final Set<String> ALLOWED_EXT = new HashSet<>(
            Arrays.asList("jpg", "jpeg", "png", "gif", "webp", "bmp"));

    /** 访问路径前缀（和 WebMvcConfiguration 里的映射保持一致） */
    public static final String URL_PREFIX = "/upload/";

    private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyy/MM");

    /** 存储根目录：<工作目录>/uploads */
    public Path rootDir() {
        return Paths.get(System.getProperty("user.dir"), "uploads").toAbsolutePath().normalize();
    }

    /**
     * 保存一个上传的文件
     *
     * @return 可直接用于 <img src> 的 URL，例如 /upload/2026/10/3f2a....png
     */
    public String save(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BaseException("请选择要上传的图片");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BaseException("图片不能超过 5MB");
        }

        String ext = extensionOf(file.getOriginalFilename());
        if (!ALLOWED_EXT.contains(ext)) {
            throw new BaseException("只支持 jpg / png / gif / webp / bmp 格式的图片");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase().startsWith("image/")) {
            throw new BaseException("上传的文件不是图片");
        }

        // 用 UUID 重命名：绝不使用用户给的文件名（路径穿越 / 覆盖他人文件）
        String ym = LocalDate.now().format(YM);
        String name = UUID.randomUUID().toString().replace("-", "") + "." + ext;

        try {
            Path dir = rootDir().resolve(ym);
            Files.createDirectories(dir);
            Path target = dir.resolve(name).normalize();
            // 双保险：即便上面的字符串被做过手脚，最终路径必须仍在根目录之下
            if (!target.startsWith(rootDir())) {
                throw new BaseException("非法的文件路径");
            }
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            String url = URL_PREFIX + ym + "/" + name;
            log.info("图片已保存：{}（{} 字节）", url, file.getSize());
            return url;
        } catch (IOException e) {
            log.error("保存上传文件失败", e);
            throw new BaseException("图片保存失败，请稍后重试");
        }
    }

    /** 取扩展名（小写，不带点）；取不到返回空串 */
    private String extensionOf(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) return "";
        return filename.substring(dot + 1).toLowerCase();
    }

}
