package com.spring.listener;

import com.spring.config.RabbitmqQueueConfig;
import com.spring.domain.config.RabbitBeanConfig;
import com.spring.domain.event.UserLoginEvent;
import com.spring.domain.eventBus.DomainEvent;
import com.spring.publisher.UserLoginPublisher;
import org.apache.commons.collections.CollectionUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.List;

@Component
public class RedisListener {

    private RedisTemplate redisTemplate;
    private RedisConnection connection;
    private  static final Logger logger = Logger.getLogger("mongodb");
    @Autowired
    private UserLoginPublisher userLoginPublisher;

    @Autowired
    public RedisListener(RedisTemplate redisTemplate, RedisConnectionFactory factory){
        this.redisTemplate = redisTemplate;
        connection = factory.getConnection();
    }

    @PostConstruct
    public void run(){
        //redis+guava事件驱动编程模拟消息队列，监听redis list,将获取到的事件发布出去
        while (true){
            try{
                byte[] rawKey = redisTemplate.getKeySerializer().serialize(RabbitBeanConfig.USER_LOGIN_E);
                List<byte[]> results = connection.bRPop(1, rawKey);
                if(CollectionUtils.isNotEmpty(results)){
                    UserLoginEvent event = (UserLoginEvent) redisTemplate.getValueSerializer().deserialize(results.get(1));
                    userLoginPublisher.asyncPublish(event);
                }
            }catch (Exception e){
                logger.error(e);
            }
        }

    }
}
