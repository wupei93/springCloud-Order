package com.spring.service.impl;

import com.google.common.base.Preconditions;
import com.spring.common.model.SurvivalClamProcessor;
import com.spring.common.model.model.RedisKey;
import com.spring.common.model.util.tools.RedisUtils;
import com.spring.domain.event.UserIntegralEvent;
import com.spring.domain.model.UserIntegral;
import com.spring.domain.type.IntegralChangeType;
import com.spring.persistence.UserIntegralMapper;
import com.spring.service.UserIntegralService;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 用户积分
 * Created by ErnestCheng on 2017/10/11.
 */
@Service
public class UserIntegralServiceImpl implements UserIntegralService {

    private static final Logger LOGGER= Logger.getLogger(UserIntegralServiceImpl.class);
    private static final String UPDATE_USER_INTEGRAL_LOCK = "updateUserIntegralLock";


    @Autowired
    private UserIntegralMapper userIntegralMapper;

    @Autowired
    private RedisTemplate redisTemplate;

    @Override
    public Integer updateUserIntegral(UserIntegralEvent userIntegralEvent) {
        Preconditions.checkNotNull(userIntegralEvent);
        Preconditions.checkArgument(userIntegralEvent.getUserId() > 0);
        String distributedLockKey = UPDATE_USER_INTEGRAL_LOCK + userIntegralEvent.getUserId();
        String lockValue = UUID.randomUUID().toString();
        try {
            RedisUtils.lock(distributedLockKey, lockValue);
            //先判断有没有这个用户积分记录
            UserIntegral userIntegral = userIntegralMapper.getUserIntegral(userIntegralEvent.getUserId());
            if (Objects.isNull(userIntegral)) {
                // 如果没有用户积分记录，并且这次改变的积分小于0,则不符合逻辑，抛出异常
                Preconditions.checkArgument(userIntegralEvent.getChangeSource() > 0);
                //添加
                userIntegral = new UserIntegral(UUID.randomUUID().toString(), userIntegralEvent.getUserId(), Timestamp.valueOf(LocalDateTime.now()), Timestamp.valueOf(LocalDateTime.now()),
                        userIntegralEvent.getChangeSource().longValue(), userIntegralEvent.getChangeSource().longValue(), 0L);
                Integer count = userIntegralMapper.addUserIntegral(userIntegral);
                return count;
            } else {  //有就更新
                //获得历史积分，只有加积分时，或者红字情况下会改变历史积分
                long hisSource = 0L;
                if (Objects.equals(userIntegralEvent.getChangeTypeStatus(), IntegralChangeType.add) || Objects.equals(userIntegralEvent.getChangeTypeStatus(), IntegralChangeType.red)) {
                    hisSource = userIntegralEvent.getChangeSource().longValue();
                }
                //获得现有积分，加积分，和消费积分，红字情况下
                long nowSource = userIntegralEvent.getChangeSource().longValue();
                //获得使用过的积分，消费积分，积分购买不能退积分
                long usedSource = 0L;
                if (Objects.equals(userIntegralEvent.getChangeTypeStatus(), IntegralChangeType.used)) {
                    //消费积分应该是小于0的
                    Preconditions.checkArgument(userIntegralEvent.getChangeSource() < 0);
                    usedSource = userIntegralEvent.getChangeSource();
                }
                return userIntegralMapper.updateUserIntegral(userIntegralEvent.getUserId(), nowSource, hisSource, usedSource);
            }
        }finally {
            RedisUtils.unLock(distributedLockKey, lockValue);
        }
    }
}
