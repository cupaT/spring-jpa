package com.example.springjpa.config

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializationContext
import java.time.Duration

@Configuration
@ConditionalOnProperty(name = ["spring.cache.type"], havingValue = "redis")
class CacheConfig {
    @Bean
    fun cacheManager(
        redisConnectionFactory: RedisConnectionFactory,
        objectMapper: ObjectMapper,
    ): RedisCacheManager {
        val cacheObjectMapper = objectMapper.copy()
            .activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder()
                    .allowIfSubType("com.example.springjpa")
                    .allowIfSubType("java.util")
                    .allowIfSubType("java.math")
                    .allowIfSubType("java.lang")
                    .build(),
                ObjectMapper.DefaultTyping.EVERYTHING,
                JsonTypeInfo.As.PROPERTY,
            )
        val serializer = GenericJackson2JsonRedisSerializer(cacheObjectMapper)
        val serializationPair = RedisSerializationContext.SerializationPair.fromSerializer(serializer)

        fun config(ttl: Duration): RedisCacheConfiguration =
            RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .serializeValuesWith(serializationPair)
                .disableCachingNullValues()

        return RedisCacheManager.builder(redisConnectionFactory)
            .cacheDefaults(config(Duration.ofMinutes(5)))
            .withCacheConfiguration("restaurants", config(Duration.ofMinutes(60)))
            .withCacheConfiguration("dishes", config(Duration.ofMinutes(30)))
            .build()
    }
}
