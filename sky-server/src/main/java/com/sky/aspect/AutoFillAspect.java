package com.sky.aspect;

import com.sky.annotation.AutoFill;
import com.sky.constant.AutoFillConstant;
import com.sky.context.BaseContext;
import com.sky.enumeration.OperationType;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

@Aspect
@Component
@Slf4j
public class AutoFillAspect {
    /**
     * 切入点
     */
    @Pointcut("execution(* com.sky.mapper.*.*(..)) && @annotation(com.sky.annotation.AutoFill)")
    public void autoFillPointcut(){}

    /**
     * 前置通知
     */
    @Before("autoFillPointcut()")
    public void autoFill(JoinPoint joinPoint){
        log.info("开始进行公共字段自动填充");

        //获取到当前被拦截的方法上的数据库操作类型
        //获取方法签名对象
        MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();

        //获得方法上的注解对象
        AutoFill autoFill = methodSignature.getMethod().getAnnotation(AutoFill.class);

        //获得数据库操作类型
        OperationType operationType = autoFill.value();

        //获取到当前被拦截方法的参数--实体对象
        Object[] args = joinPoint.getArgs();
        if(args == null || args.length == 0){
            return;
        }

        Object entity = args[0];

        //准备赋值的数据
        Long currentId = BaseContext.getCurrentId();
        LocalDateTime now = LocalDateTime.now();

        //通过不同的操作类型，为对应的属性通过反射来进行赋值
        if(operationType == OperationType.INSERT){
            //为四个公共字段赋值（实体上没有该字段时自动跳过）
            fillIfPresent(entity, AutoFillConstant.SET_CREATE_TIME, LocalDateTime.class, now);
            fillIfPresent(entity, AutoFillConstant.SET_UPDATE_TIME, LocalDateTime.class, now);
            fillIfPresent(entity, AutoFillConstant.SET_CREATE_USER, Long.class, currentId);
            fillIfPresent(entity, AutoFillConstant.SET_UPDATE_USER, Long.class, currentId);
        }else if(operationType == OperationType.UPDATE){
            //为两个公共字段赋值（实体上没有该字段时自动跳过）
            fillIfPresent(entity, AutoFillConstant.SET_UPDATE_TIME, LocalDateTime.class, now);
            fillIfPresent(entity, AutoFillConstant.SET_UPDATE_USER, Long.class, currentId);
        }

    }

    /**
     * 实体上存在该 setter 才赋值，不存在就跳过。
     *
     * 为什么必须容错：不同实体的"公共字段"并不完全一致。
     * 例如按新表设计，category 只有 create_time / update_time，
     * 没有 create_user / update_user。
     *
     * 早期实现是把 4 个 setter 先全部查出来再统一赋值，只要有一个方法不存在就抛
     * NoSuchMethodException，被 catch 吞掉后连 create_time 都没填上，
     * 最终 insert 报 "Column 'create_time' cannot be null"。
     * 改成逐个判断后，缺字段的实体也能正常填充其余公共字段。
     */
    private void fillIfPresent(Object entity, String setterName, Class<?> paramType, Object value){
        try{
            //用 getMethod（会查找继承来的 public 方法）而不是 getDeclaredMethod
            Method setter = entity.getClass().getMethod(setterName, paramType);
            setter.invoke(entity, value);
        }catch(NoSuchMethodException e){
            //该实体没有这个公共字段，属于正常情况，跳过即可
            log.debug("实体 {} 没有 {}，跳过该公共字段",
                    entity.getClass().getSimpleName(), setterName);
        }catch(Exception e){
            log.error("公共字段自动填充失败，方法：{}", setterName, e);
        }
    }

}
