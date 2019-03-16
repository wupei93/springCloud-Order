package com.spring.config;

import com.alibaba.druid.util.StringUtils;
import com.spring.common.model.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.MethodParameter;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.concurrent.TimeUnit;

/**
 * Created by jiangyunxiong on 2018/5/22.
 */
@Service
public class UserArgumentResolver implements HandlerMethodArgumentResolver {

    @Autowired
    public RedisTemplate redisTemplate;
    /**
     * 当参数类型为User才做处理
     *
     * @param methodParameter
     * @return
     */
    @Override
    public boolean supportsParameter(MethodParameter methodParameter) {
        //获取参数类型
        Class<?> clazz = methodParameter.getParameterType();
        return clazz == User.class;
    }

    /**
     * 思路：先获取到已有参数HttpServletRequest，从中获取到token，再用token作为key从redis拿到User，而HttpServletResponse作用是为了延迟有效期
     */
    @Override
    public Object resolveArgument(MethodParameter methodParameter, ModelAndViewContainer modelAndViewContainer, NativeWebRequest nativeWebRequest, WebDataBinderFactory webDataBinderFactory) throws Exception {
        HttpServletRequest request = nativeWebRequest.getNativeRequest(HttpServletRequest.class);
        HttpServletResponse response = nativeWebRequest.getNativeResponse(HttpServletResponse.class);

        String paramToken = request.getParameter("token");
        Cookie[] cookies = request.getCookies();
        String cookieToken = null;
        for(Cookie cookie : cookies){
            if("Token".equals(cookie.getName())){
                cookieToken = cookie.getValue();
                cookie.setMaxAge(3600*24 *2);
                response.addCookie(cookie);
                break;
            }
        }
        if (StringUtils.isEmpty(cookieToken) && StringUtils.isEmpty(paramToken)) {
            return null;
        }
        String token = StringUtils.isEmpty(paramToken) ? cookieToken : paramToken;
        String key = "Token" + token;
        User user = (User)redisTemplate.opsForValue().get(key);
        redisTemplate.opsForValue().set(key, user, 30, TimeUnit.MINUTES);
        return user;
    }
}