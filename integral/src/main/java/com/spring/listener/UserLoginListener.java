package com.spring.listener;

import com.alibaba.fastjson.JSON;
import com.google.common.eventbus.Subscribe;
import com.rabbitmq.client.Channel;
import com.spring.common.model.util.tools.JavaScriptEngine;
import com.spring.domain.config.RabbitBeanConfig;
import com.spring.domain.event.UserLoginEvent;
import com.spring.domain.model.IntegralChange;
import com.spring.domain.model.UserIntegralDetail;
import com.spring.publisher.UserLoginPublisher;
import com.spring.repository.IntegralListenerRepository;
import com.spring.service.IntegralChangeService;
import com.spring.service.UserIntegralDetailService;
import org.apache.log4j.Logger;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.annotation.RabbitListeners;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * @author ErnestCheng
 */
@Component
@RabbitListeners(@RabbitListener(queues = RabbitBeanConfig.USER_LOGIN_Q))
public class UserLoginListener {

    private static final Logger LOGGER= Logger.getLogger(UserLoginListener.class);

    @Autowired
    private IntegralChangeService integralChangeService;

    @Autowired
    private UserIntegralDetailService userIntegralDetailService;

    @Autowired
    private IntegralListenerRepository integralListenerRepository;

    @Autowired
    private UserLoginPublisher userLoginPublisher;

    @PostConstruct
    public void init(){
        userLoginPublisher.register(this);
    }

    /**
     * 从redis中获取的消息
     */
    @Subscribe
    public void listenEvent(UserLoginEvent userLoginEvent){
        handleLoginEvent(userLoginEvent);
    }

    /**
     * 从RabbitMq中获取的消息
     */
    @RabbitHandler
    public void process(Object message,Channel channel) throws IOException {
        Message messageMq = null;
        try{
            messageMq=(Message)message;
            String body=new String(messageMq.getBody());
            UserLoginEvent userLoginEvent =this.checkMessageAndChange(body);
            handleLoginEvent(userLoginEvent);
            //确认消息处理 ack
            channel.basicAck(messageMq.getMessageProperties().getDeliveryTag(),false);
        }catch (Exception e){
            channel.basicNack(messageMq.getMessageProperties().getDeliveryTag(), false,false);
            throw e;
        }
    }

    private void handleLoginEvent(UserLoginEvent userLoginEvent){
        if(Objects.nonNull(userLoginEvent)){
            //处理用户积分详情
            IntegralChange integralChange=integralChangeService.getIntegralChangeByCode(userLoginEvent.getCode());
            if(Objects.nonNull(integralChange)) {
                UserIntegralDetail userIntegralDetail = new UserIntegralDetail(UUID.randomUUID().toString(), userLoginEvent.getUserId(), userLoginEvent.getCreateTime(), this.getSource(integralChange.getMath(), userLoginEvent.getNum()), userLoginEvent.getRemark(), integralChange.getId());
                userIntegralDetailService.addUserIntegralDetail(userIntegralDetail);
            }else{
                handlerErrorMessage(userLoginEvent);
            }
        }
    }

    /**
     * 获得积分
     * @param math
     * @param num
     * @return
     */
    private Integer  getSource(String math,BigDecimal num){
        Map map=new HashMap<>(1);
        map.put("num",num);
        return  JavaScriptEngine.getMathFunctionValue(map,math).intValue();
    }


    /**
     * 检查事件对象是否正确
     * @param message
     * @return
     */
    private UserLoginEvent checkMessageAndChange(Object message){
        UserLoginEvent userLoginEvent=JSON.parseObject(message.toString(), UserLoginEvent.class);
        if(Objects.nonNull(userLoginEvent)){
            if(Objects.nonNull(userLoginEvent.getUserId()) && Objects.nonNull(userLoginEvent.getCode())){
                return userLoginEvent;
            }else{
                handlerErrorMessage(userLoginEvent);
                return null;
            }
        }else{
            userLoginEvent =new UserLoginEvent();
            userLoginEvent.setCreateTime(Timestamp.valueOf(LocalDateTime.now()));
            userLoginEvent.setRemark("信息为空");
            handlerErrorMessage(userLoginEvent);
            return null;
        }
    }

    /**
     * 处理错误的消息
     */
    private void handlerErrorMessage(UserLoginEvent message){
        LOGGER.error("message is error,content:"+message);
        integralListenerRepository.insert(message);
    }

}
