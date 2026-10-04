package com.jnimble.plugin.scan.service;

import com.jnimble.platform.persistence.crud.MapperUtils;
import com.jnimble.plugin.scan.mapper.ConsumerMapper;
import com.jnimble.plugin.scan.model.entity.ConsumerEntity;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ConsumerService {

    private final ConsumerMapper consumerMapper;

    public ConsumerService(ConsumerMapper consumerMapper) {
        this.consumerMapper = consumerMapper;
    }

    public ConsumerEntity getConsumerByAccessToken(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return null;
        }
        List<ConsumerEntity> consumers = MapperUtils.selectList(consumerMapper, ConsumerEntity.class,
                wrapper -> wrapper.eq("access_token", accessToken));
        return consumers.isEmpty() ? null : consumers.getFirst();
    }

    public ConsumerEntity getConsumer(Long id) {
        return MapperUtils.getById(consumerMapper, id, "Consumer not found: " + id);
    }

    public ConsumerEntity getOrCreateDemoConsumer() {
        List<ConsumerEntity> consumers = MapperUtils.selectList(consumerMapper, ConsumerEntity.class,
                wrapper -> wrapper.eq("access_token", "demo-access-token-888888"));
        if (!consumers.isEmpty()) {
            return consumers.getFirst();
        }
        ConsumerEntity entity = new ConsumerEntity();
        entity.setUuid("demo-consumer-uuid");
        entity.setNickName("测试用户");
        entity.setAccessToken("demo-access-token-888888");
        return MapperUtils.insert(consumerMapper, entity);
    }
}
