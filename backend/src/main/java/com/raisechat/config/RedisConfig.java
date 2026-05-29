package com.raisechat.config;

import com.raisechat.message.ws.MessageChannelRedisSubscriber;
import com.raisechat.message.ws.MessageDmRedisSubscriber;
import com.raisechat.message.ws.MessageThreadRedisSubscriber;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class RedisConfig {

    @Value("${app.redis.channel-prefix:messages:channel:}")
    private String channelPrefix;

    @Value("${app.redis.dm-prefix:messages:dm:}")
    private String dmPrefix;

    @Value("${app.redis.thread-prefix:messages:thread:}")
    private String threadPrefix;

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory factory,
            MessageChannelRedisSubscriber channelSubscriber,
            MessageDmRedisSubscriber dmSubscriber,
            MessageThreadRedisSubscriber threadSubscriber
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        container.addMessageListener(channelSubscriber, new PatternTopic(channelPrefix + "*"));
        container.addMessageListener(dmSubscriber, new PatternTopic(dmPrefix + "*"));
        container.addMessageListener(threadSubscriber, new PatternTopic(threadPrefix + "*"));
        return container;
    }
}
