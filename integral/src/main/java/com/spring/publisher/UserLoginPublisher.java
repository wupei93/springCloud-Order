package com.spring.publisher;

import com.spring.domain.eventBus.GuavaDomainEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class UserLoginPublisher extends GuavaDomainEventPublisher {

    @Override
    public String identify() {
        return "user_login_publisher";
    }
}
