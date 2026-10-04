package com.jnimble.plugin.scan.service;

import com.jnimble.platform.persistence.crud.MapperUtils;
import com.jnimble.plugin.scan.mapper.ScanTableMapper;
import com.jnimble.plugin.scan.model.entity.ScanTableEntity;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ScanTableService {

    private final ScanTableMapper tableMapper;

    public ScanTableService(ScanTableMapper tableMapper) {
        this.tableMapper = tableMapper;
    }

    public ScanTableEntity getTableByUuid(String uuid) {
        List<ScanTableEntity> tables = MapperUtils.selectList(tableMapper, ScanTableEntity.class,
                wrapper -> wrapper.eq("uuid", uuid));
        if (tables.isEmpty()) {
            throw new IllegalArgumentException("Table not found for UUID: " + uuid);
        }
        return tables.getFirst();
    }

    public ScanTableEntity getTable(Long id) {
        return MapperUtils.getById(tableMapper, id, "Table not found: " + id);
    }
}
