package com.spring.common.model.util.tools;

import com.spring.common.model.SurvivalClamProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class RedisUtils {

    private static RedisTemplate redisTemplate;

    private static String SURVIVAL = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('expire', KEYS[1],ARGV[2]) else return '0' end";

    @Autowired
    public void setRedisTemplate(RedisTemplate rt){
        redisTemplate = rt;
    }

    public static int expandLockTime(String key, String value, int lockTime){
        List keys = new ArrayList(1);
        keys.add(key);
        return (Integer) redisTemplate.execute(new DefaultRedisScript<Integer>(SURVIVAL, Integer.class), keys, value, lockTime);
    }

    /**
     * @param lockValue 的作用是防止守护线程错误的给其他线程的锁续命
     */
    public static void lock(String distributedLockKey, String lockValue){
        //获得用户对象锁
        boolean locked = redisTemplate.opsForValue().setIfAbsent(distributedLockKey, lockValue);
        int lockTime = 30;
        while (!locked) {
            try {
                Thread.sleep(1000);
                locked = redisTemplate.opsForValue().setIfAbsent(distributedLockKey, lockValue);
                // 不管有没有拿到锁都有设置超时时间，防止其他线程拿到锁后还未设置超时时间就挂掉了
                int tempTime = redisTemplate.getExpire(distributedLockKey).intValue();
                if (tempTime == -1) {
                    redisTemplate.expire(distributedLockKey, lockTime, TimeUnit.SECONDS);
                }
            } catch (InterruptedException e) {
                log.error("线出问题:" + e.getCause());
            }
        }
        // 起一个线程续命，如果服务器挂了续命线程也会一起挂掉
        new Thread(new SurvivalClamProcessor(distributedLockKey, lockValue,lockTime)).start();
    }

    /**
     * @param lockValue 的作用是防止错误的删除了其他线程的锁
     */
    public static void unLock(String distributedLockKey, String lockValue){
        if(lockValue.equals(redisTemplate.opsForValue().get(distributedLockKey))){
            redisTemplate.delete(distributedLockKey);
        }
    }

    public static boolean tryLock(String distributedLockKey, String lockValue){
        boolean locked = redisTemplate.opsForValue().setIfAbsent(distributedLockKey, lockValue);
        int lockTime = 30;
        int tempTime = redisTemplate.getExpire(distributedLockKey).intValue();
        if (tempTime == -1) {
            redisTemplate.expire(distributedLockKey, lockTime, TimeUnit.SECONDS);
        }
        if(locked){
            new Thread(new SurvivalClamProcessor(distributedLockKey, lockValue,lockTime)).start();
        }
        return locked;
    }
}
