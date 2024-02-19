package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.constants.RedisConstants;
import com.hmdp.dto.RedisDataDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.constants.RedisConstants.LOCK_SHOP_KEY;
import static com.hmdp.constants.RedisConstants.LOCK_SHOP_TTL;

@Component
public class MyRedisUtils {
    @Autowired
    private StringRedisTemplate srt;

    /**
     * @Author: 梁雨佳
     * @Date: 2024/2/19 17:21:19
     * @Description: 将任意Java对象序列化为JSON，并设置TTL
     */
    public void set (String key, Object data, Long time, TimeUnit unit) {
        srt.opsForValue().set(key, JSONUtil.toJsonStr(data), time, unit);
    }

    /**
     * @Author: 梁雨佳
     * @Date: 2024/2/19 17:21:40
     * @Description: 将任意Java对象序列化为JSON，并设置TTL;使用逻辑过期处理缓存击穿问题
     */
    public void setWithLogicalExpire (String key, Object data, Long time, TimeUnit unit) {
        RedisDataDTO redisData = new RedisDataDTO();
        redisData.setData(data);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        srt.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    /**
     * @Author: 梁雨佳
     * @Date: 2024/2/19 17:24:25
     * @Description: 指定key查询缓存，反序列化为指定类型，利用缓存空值方式解决缓存穿透问题
     */
    public <T, ID> T getWithPassThrough (String keyPrefix, ID id, Class<T> type,
                                         Long time, TimeUnit unit, Function<ID, T> dbCallback) {
        String key = keyPrefix + id;
        String json = srt.opsForValue().get(key);
        if (json == null) {
            return null;
        }

        // 因为不知道查询的逻辑，所以要求传入这段逻辑
        T result = dbCallback.apply(id);
        // 查询结果为空则缓存空值
        if (result == null) {
            srt.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }

        // 否则写入Redis
        this.set(key, result, time, unit);
        return result;
    }

    /**
     * @Author: 梁雨佳
     * @Date: 2024/2/19 17:58:16
     * @Description: 指定key查询缓存，反序列化为指定类型，利用逻辑过期解决缓存击穿问题
     */
    public <T, ID> T getWithLogicalExpire (String keyPrefix, ID id, Class<T> type,
                                           Long time, TimeUnit unit, Function<ID, T> dbCallback) throws InterruptedException {
        String key = keyPrefix + id;
        String lockKey = LOCK_SHOP_KEY + id;

        String json = srt.opsForValue().get(key);
        if (json == null) {
            return null;
        }

        RedisDataDTO redisData = JSONUtil.toBean(json, RedisDataDTO.class);
        T result = JSONUtil.toBean(redisData.getData().toString(), type);
        if (redisData.getExpireTime().isAfter(LocalDateTime.now())) {
            // 没过期，直接返回结果
            return result;
        }

        // 过期了，使用互斥锁，只能有一个线程能去查询数据库
        boolean getLock = false;
        int retries = 5; // 重试次数
        int retryDelayMs = 200; // 重试前要等待多少毫秒

        for (int i = 0; i < retries; i++) {
            getLock = tryLock(lockKey);
            if (getLock) {
                break;
            }
            Thread.sleep(retryDelayMs);
        }

        // 没得到锁，返回旧的数据；这个就是逻辑删除的问题：不能保证数据一致性
        if (!getLock) {
            return JSONUtil.toBean(redisData.getData().toString(), type);
        }

        // 得到锁，重新构建缓存
        T res;
        try {
            // 再次检查缓存是否存在，这是有必要的
            String cacheData2 = srt.opsForValue().get(key);
            if (cacheData2 != null || !cacheData2.isEmpty()) {
                RedisDataDTO bean = JSONUtil.toBean(cacheData2, RedisDataDTO.class);
                if (bean.getExpireTime().isAfter(LocalDateTime.now())) {
                    return JSONUtil.toBean(bean.getData().toString(), type);
                }
            }
            res = dbCallback.apply(id);
            this.setWithLogicalExpire(key, res, time, unit);
        } finally {
            unlock(lockKey);
        }
        return res;
    }

    private boolean tryLock (String lockKey) {
        // 如果key不存在，才设置kv
        Boolean flag = srt.opsForValue().setIfAbsent(lockKey, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unlock (String lockKey) {
        srt.delete(lockKey);
    }
}
