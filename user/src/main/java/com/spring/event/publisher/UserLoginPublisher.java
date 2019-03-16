package com.spring.event.publisher;


import com.alibaba.fastjson.JSON;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.spring.domain.config.RabbitBeanConfig;
import com.spring.event.UserLoginEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.support.CorrelationData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;

/**
 * 用户登录消息发送者
 */
@Component
public class UserLoginPublisher {

    @Autowired
    private RabbitTemplate rabbitmqTemplate;
    @Autowired
    private RedisTemplate redisTemplate;

    @PostConstruct
    public void init(){
        rabbitmqTemplate.setConfirmCallback(
                (correlationData, ack, cause) -> {
                    //todo 补充确认逻辑
                }
        );
        rabbitmqTemplate.setReturnCallback(
                (message, replyCode, replyText,exchange, routingKey) -> {
                    //todo 补充返回消息回调逻辑
                }
        );
    }

    /**
     * 发送用户积分事件
     */
    @HystrixCommand(fallbackMethod = "sendUserLoginEventByRedis")
    public void sendUserLoginEvent(String code,Integer userId,String remark){
        UserLoginEvent userLoginEvent =new UserLoginEvent(userId,
                Timestamp.valueOf(LocalDateTime.now()),remark,code,new BigDecimal(1));
        rabbitmqTemplate.convertAndSend(RabbitBeanConfig.USER_LOGIN_E,"", JSON.toJSONString(userLoginEvent));
    }

    public void sendUserLoginEventByRedis(String code, Integer userId, String remark){
        UserLoginEvent userLoginEvent =new UserLoginEvent(userId,
                Timestamp.valueOf(LocalDateTime.now()),remark,code,new BigDecimal(1));
        redisTemplate.opsForList().leftPush(RabbitBeanConfig.USER_LOGIN_E, JSON.toJSONString(userLoginEvent));
    }
}
