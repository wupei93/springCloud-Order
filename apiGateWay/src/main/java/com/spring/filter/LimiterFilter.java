package com.spring.filter;

import com.google.common.util.concurrent.RateLimiter;
import com.netflix.zuul.ZuulFilter;
import com.netflix.zuul.context.RequestContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletRequest;

import java.util.concurrent.TimeUnit;

import static org.springframework.cloud.netflix.zuul.filters.support.FilterConstants.PRE_DECORATION_FILTER_ORDER;
import static org.springframework.cloud.netflix.zuul.filters.support.FilterConstants.PRE_TYPE;

/**
 * Created by 廖师兄
 * 2018-02-15 15:34
 */
@Component
public class LimiterFilter extends ZuulFilter {
    RateLimiter rateLimiter = RateLimiter.create(500);

    @Override
    public String filterType() {
        return PRE_TYPE;
    }

    @Override
    public int filterOrder() {
        return PRE_DECORATION_FILTER_ORDER - 1;
    }

    @Override
    public boolean shouldFilter() {
        return true;
    }

    @Override
    public Object run() {
        // 令牌桶算法限流
        boolean isAcquired = rateLimiter.tryAcquire(1000, 1, TimeUnit.SECONDS);
        if(!isAcquired) {
            RequestContext requestContext = RequestContext.getCurrentContext();
            requestContext.setSendZuulResponse(false);
            requestContext.setResponseStatusCode(HttpStatus.REQUEST_TIMEOUT.value());
        }
        return null;
    }
}
