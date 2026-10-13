package com.sky.handler;

import com.sky.constant.MessageConstant;
import com.sky.exception.BaseException;
import com.sky.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.sql.SQLIntegrityConstraintViolationException;

/**
 * 全局异常处理器，处理项目中抛出的业务异常
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 捕获业务异常
     * @param ex
     * @return
     */
    @ExceptionHandler
    public Result exceptionHandler(BaseException ex){
        log.error("异常信息：{}", ex.getMessage());
        return Result.error(ex.getMessage());
    }

    /**
     * 处理SQL异常
     */
    @ExceptionHandler
    public Result sqlExceptionHandler(SQLIntegrityConstraintViolationException ex){
        //重复信息录入
        String message = ex.getMessage();
        if(message.contains("Duplicate entry")){
            //先对字符串进行分割
            String[] split = message.split(" ");
            //获取重复的字段值
            String username = split[2];
            String msg = username + MessageConstant.ALREADY_EXIST;
            return Result.error(msg);
        }else{
            return Result.error(MessageConstant.UNKNOWN_ERROR);
        }
    }

    /**
     * 兜底处理：未被上面两类覆盖的异常，保证任何异常都返回统一的 Result 结构，
     * 避免前端拿到 Spring Boot 默认的 /error 结构导致处理不一致
     */
    @ExceptionHandler
    public Result exceptionHandler(Exception ex){
        log.error("系统异常：", ex);
        return Result.error(MessageConstant.UNKNOWN_ERROR);
    }

}
