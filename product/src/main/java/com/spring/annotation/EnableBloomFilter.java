package com.spring.annotation;

import com.google.common.hash.BloomFilter;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface EnableBloomFilter {
    // 要过滤的参数索引位置
    int index() default 0;
}
