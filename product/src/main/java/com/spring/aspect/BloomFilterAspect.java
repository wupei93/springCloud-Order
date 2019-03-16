package com.spring.aspect;

import com.google.common.hash.BloomFilter;
import com.spring.annotation.EnableBloomFilter;
import com.spring.common.model.exception.BloomFilterException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;

/**
 * 布隆过滤器，防止缓存穿透
 */
@Aspect
@Order(2)
@Component
public class BloomFilterAspect {

    @Pointcut("@annotation(com.spring.annotation.EnableBloomFilter)")
    public void bloomFilter(){}

    @Around("bloomFilter()")
    public void filter(ProceedingJoinPoint joinPoint){
        Object target = joinPoint.getTarget();
        Object[] args = joinPoint.getArgs();
        MethodSignature methodSignature = (MethodSignature)joinPoint.getSignature();
        EnableBloomFilter enableBloomFilter = methodSignature.getMethod().getAnnotation(EnableBloomFilter.class);
        // 待过滤的目标参数
        Object targetArg = args[enableBloomFilter.index()];
        BloomFilter filter = findBloomFilter(target);
        boolean needIntercept = false;
        if(filter != null){
            needIntercept = !filter.mightContain(targetArg);
        }
        if(!needIntercept){
            try{
                joinPoint.proceed();
            } catch (Throwable e){
                throw new BloomFilterException(e);
            }
        }
    }


    private BloomFilter findBloomFilter(Object target) {
        if(target == null){
            return null;
        }
        Field[] feilds = target.getClass().getDeclaredFields();
        BloomFilter bloomFilter = null;
        for(Field field : feilds){
            field.setAccessible(true);
            if(field.getType().equals(BloomFilter.class)){
                if(bloomFilter != null){
                    throw new BloomFilterException("too many bloom filter found!");
                }
                try {
                    bloomFilter = (BloomFilter) field.get(target);
                } catch (IllegalAccessException e){
                    throw new BloomFilterException(e);
                }
            }
        }
        return bloomFilter;
    }
}
