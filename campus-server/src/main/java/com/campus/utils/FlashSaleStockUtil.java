package com.campus.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;

/**
 * 捡漏抢购的库存工具（Redis 预扣 + Lua 原子扣减）
 *
 * ==========================================================================
 * 【为什么必须用 StringRedisTemplate，而不是项目里那个 RedisTemplate】
 *   项目的 RedisConfiguration 只设置了 key 的序列化器（String），
 *   value 用的是 Spring 默认的 JDK 序列化 —— 存进去是一串二进制字节。
 *   后果：Lua 脚本里的 tonumber() 读不了它，DECR 也会直接报错。
 *   StringRedisTemplate 的 key/value 都是纯字符串，正好适合做计数器。
 *
 * 【为什么用 Lua，而不是"先 get 再 decr"】
 *   get + decr 是两条独立命令，中间可能被别的请求插进来
 *   —— 这就是经典的"先检查后执行"竞态（检查时还有货，扣的时候已经没了）。
 *   Lua 脚本在 Redis 内部是【整体原子执行】的：脚本跑的时候不会有别的命令插队。
 *   于是"判断库存 > 0"和"扣减"变成一个不可分割的动作。
 *
 *   （补充：如果逻辑很简单，单条 DECR 本身也是原子的，可以"扣完看结果是否 <0 再回补"。
 *     之所以还是用 Lua，是因为真实秒杀往往要在一个原子操作里做更多判断，
 *     比如"这个人有没有抢过"，那时单命令就不够了。）
 *
 * 【库存为什么是 1】
 *   二手商品都是"一件货"，所以库存固定 1。方法保留了 initialStock 参数，
 *   将来要支持"多件同款"时不用改这里。
 * ==========================================================================
 */
@Component
@Slf4j
public class FlashSaleStockUtil {

    /** 库存 key 前缀：一个商品一个 key */
    private static final String STOCK_KEY_PREFIX = "flash_sale_stock:";

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 扣减库存的 Lua 脚本
     *
     * KEYS[1] = 库存 key
     * 返回：1 扣减成功 ／ 0 库存已空 ／ -1 key 不存在（需要先初始化）
     *
     *   local stock = redis.call('GET', KEYS[1])   -- 读当前库存（字符串，或 false=不存在）
     *   if stock == false then return -1 end       -- key 不在，交给调用方去初始化
     *   if tonumber(stock) > 0 then                -- 还有货
     *       redis.call('DECR', KEYS[1])            -- 扣一件
     *       return 1
     *   end
     *   return 0                                   -- 没货了
     */
    private static final DefaultRedisScript<Long> DEDUCT_SCRIPT = new DefaultRedisScript<>(
            "local stock = redis.call('GET', KEYS[1]) " +
                    "if stock == false then return -1 end " +
                    "if tonumber(stock) > 0 then redis.call('DECR', KEYS[1]) return 1 end " +
                    "return 0",
            Long.class);

    /**
     * 预扣库存（惰性初始化 + 原子扣减）
     *
     * 为什么"惰性初始化"：库存 key 不需要在商品发布时就建好，
     * 第一次有人来抢时用 SETNX 建（key 不存在才写，并发安全）。
     * 这样就不用维护"商品上下架时同步 Redis 库存"那一堆逻辑 —— 少一处状态就少一处不一致。
     *
     * @param initialStock 初始库存（二手商品传 1）
     * @return true = 扣减成功（抢到了）；false = 没货了
     */
    public boolean deduct(Long productId, int initialStock) {
        String key = stockKey(productId);

        // SETNX：只在 key 不存在时写入；并发下只有一个请求能写成功，不会互相覆盖
        stringRedisTemplate.opsForValue().setIfAbsent(key, String.valueOf(initialStock));

        Long result = stringRedisTemplate.execute(DEDUCT_SCRIPT, Collections.singletonList(key));
        if (result == null) {
            // 理论上脚本一定会返回，防御一下（比如连接异常）
            log.warn("Lua 扣减库存返回 null，productId={}", productId);
            return false;
        }
        if (result == -1L) {
            // 极端情况：SETNX 之后 key 又被清掉了；再建一次并重试
            stringRedisTemplate.opsForValue().setIfAbsent(key, String.valueOf(initialStock));
            result = stringRedisTemplate.execute(DEDUCT_SCRIPT, Collections.singletonList(key));
        }

        boolean ok = (result != null && result == 1L);
        log.info("Redis 预扣库存：productId={}, 结果={}", productId, ok ? "扣减成功" : "库存不足");
        return ok;
    }

    /**
     * 补偿：把刚才扣掉的库存还回去
     *
     * 什么时候用：Redis 扣减成功、但后面的数据库操作失败了。
     * ⚠️ Redis 不参与数据库事务，不会跟着回滚，必须手动补偿 —— 这就是"最终一致性"的做法。
     * 不补偿的后果：Redis 认为卖光了，但数据库里商品还在售，捡漏专区会一直显示"已抢光"。
     */
    public void restore(Long productId) {
        stringRedisTemplate.opsForValue().increment(stockKey(productId));
        log.warn("数据库操作失败，已把 Redis 库存加回去：productId={}", productId);
    }

    /** 查当前剩余库存（展示 / 排查问题用）；key 不存在返回 null */
    public String getStock(Long productId) {
        return stringRedisTemplate.opsForValue().get(stockKey(productId));
    }

    /** 商品下架或活动结束时清掉库存 key（下次抢购会按新的初始库存重建） */
    public void clear(Long productId) {
        stringRedisTemplate.delete(stockKey(productId));
    }

    private String stockKey(Long productId) {
        return STOCK_KEY_PREFIX + productId;
    }
}
