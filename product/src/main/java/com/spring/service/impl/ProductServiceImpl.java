package com.spring.service.impl;

import com.google.common.base.Charsets;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnel;
import com.google.common.hash.Funnels;
import com.spring.annotation.EnableBloomFilter;
import com.spring.common.model.exception.GlobalException;
import com.spring.common.model.model.RedisKey;
import com.spring.common.model.util.tools.BeanToMapUtil;
import com.spring.common.model.util.tools.RedisUtils;
import com.spring.domain.Product;
import com.spring.domain.request.ProductUpdateRequest;
import com.spring.persistence.ProductMapper;
import com.spring.service.ProductService;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.beans.IntrospectionException;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * @Description 产品service实现
 * @author ErnestCheng
 * @Date 2017/5/27.
 */
@Service
public class ProductServiceImpl implements ProductService {

    private static final Logger logger= Logger.getLogger(ProductServiceImpl.class);
    private static final String GET_PRODUCT_BY_ID = "getProductById";

    private BloomFilter bloomFilter;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * 每天早晨6点执行一次，防止用于数据的改变导致布隆过滤器效果变差
     */
    @PostConstruct
    @Scheduled(cron = "* * 3 * * *")
    public void init(){
        List<Product> products = productMapper.queryProductList();
        bloomFilter = BloomFilter.create(Funnels.integerFunnel(), products.size());
        products.forEach(product -> {
            int productId = product.getId();
            // 初始化布隆过滤器
            bloomFilter.put(productId);
            // 随便预热缓存
            try {
                redisTemplate.opsForHash().putAll(RedisKey.product+productId, BeanToMapUtil.convertBean(product));
            } catch (Exception e) {
                throw new GlobalException(e.toString());
            }
        });
    }

    @Override
    public void addProduct(Product product) {
        productMapper.addProduct(product);
        bloomFilter.put(product.getId());
    }

    @Override
    @EnableBloomFilter
    public Product getProductById(Integer productId) throws IllegalAccessException, IntrospectionException, InvocationTargetException, InstantiationException {
        String key= RedisKey.producth+productId;
        Map productM=redisTemplate.opsForHash().entries(key);
        if(Objects.isNull(productM) || productM.isEmpty()) {
            // 使用分布式锁，只允许一个线程访问数据库，防止缓存击穿
            String distributedLockKey = GET_PRODUCT_BY_ID + productId;
            String lockValue = UUID.randomUUID().toString();
            try{
                while (true){
                    if(RedisUtils.tryLock(distributedLockKey, lockValue)){
                        productM = redisTemplate.opsForHash().entries(key);
                        if(Objects.isNull(productM) || productM.isEmpty()) {
                            logger.info("查询数据库");
                            Product product=productMapper.getProductById(productId);
                            if(Objects.nonNull(product)) {
                                redisTemplate.opsForHash().putAll(key, BeanToMapUtil.convertBean(product));
                            } else {
                                // 即使没查到数据也放一个空对象到redis中，与布隆过滤器共同防止缓存穿透
                                redisTemplate.opsForHash().putAll(key, BeanToMapUtil.convertBean(new Product()));
                                redisTemplate.expire(key, 1, TimeUnit.MINUTES);
                            }
                        }
                        break;
                    } else {
                        Thread.sleep(50);
                    }
                }
            } catch (Exception e){
                throw new GlobalException(e.getMessage());
            } finally{
                RedisUtils.unLock(distributedLockKey, lockValue);
            }
        }
        return (Product) BeanToMapUtil.convertMap(Product.class,productM);
    }

    @Override
    public int updateProduct(ProductUpdateRequest productUpdateRequest) {
        int flag=productMapper.updateProduct(productUpdateRequest.getProductId(),productUpdateRequest.getProductName(),productUpdateRequest.getStock(),productUpdateRequest.getPrice());
        if(flag==1){
            String key=RedisKey.producth+productUpdateRequest.getProductId();
            if(redisTemplate.opsForHash().hasKey(key,"productId")) {
                redisTemplate.opsForHash().put(key,"stock",productUpdateRequest.getStock());
                redisTemplate.opsForHash().put(key,"name",productUpdateRequest.getProductName());
                redisTemplate.opsForHash().put(key,"price",productUpdateRequest.getPrice());
                redisTemplate.opsForHash().put(key,"updateTime",new Date());
            }
        }
        return flag;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED,rollbackFor = RuntimeException.class)
    public int deleteProductByProductId(Integer productId) {
        // 1：先删除数据库中的产品
        int flag=productMapper.deleteProductByProductId(productId);
        // 2: 删除redis中数据
        if(flag==1){
            redisTemplate.delete(RedisKey.producth+productId);
        }
        return flag;
    }
}
