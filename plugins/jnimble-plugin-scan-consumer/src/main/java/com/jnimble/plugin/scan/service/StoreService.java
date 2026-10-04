package com.jnimble.plugin.scan.service;

import com.jnimble.platform.persistence.crud.MapperUtils;
import com.jnimble.plugin.scan.mapper.StoreMapper;
import com.jnimble.plugin.scan.model.entity.StoreEntity;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoreService {

    private final StoreMapper storeMapper;

    public StoreService(StoreMapper storeMapper) {
        this.storeMapper = storeMapper;
    }

    public StoreEntity getStore(Long id) {
        return MapperUtils.getById(storeMapper, id, "Store not found: " + id);
    }

    public List<StoreEntity> listStores() {
        return MapperUtils.selectList(storeMapper, StoreEntity.class,
                wrapper -> wrapper.eq("status", "ENABLED").orderByDesc("created_at"));
    }

    @Transactional
    public void updateLatLng(Long storeId, BigDecimal lat, BigDecimal lng) {
        StoreEntity update = new StoreEntity();
        update.setId(storeId);
        update.setLat(lat);
        update.setLng(lng);
        update.setUpdatedAt(LocalDateTime.now());
        MapperUtils.updateById(storeMapper, update);
    }
}
