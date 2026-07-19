package com.jnimble.plugin.printer.service;

import com.jnimble.platform.persistence.crud.MapperUtils;
import com.jnimble.plugin.printer.mapper.PrintNodeMapper;
import com.jnimble.plugin.printer.model.entity.PrintNodeEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PrintNodeService {

    private final PrintNodeMapper printNodeMapper;

    public PrintNodeService(PrintNodeMapper printNodeMapper) {
        this.printNodeMapper = printNodeMapper;
    }

    public List<PrintNodeEntity> listNodes() {
        return MapperUtils.selectList(printNodeMapper, PrintNodeEntity.class, wrapper -> wrapper.orderByAsc("node_name"));
    }

    public List<PrintNodeEntity> listEnabledNodes() {
        return MapperUtils.selectList(printNodeMapper, PrintNodeEntity.class, wrapper -> wrapper.eq("enabled", true));
    }

    public PrintNodeEntity getNodeByName(String nodeName) {
        return MapperUtils.selectOne(printNodeMapper, PrintNodeEntity.class, wrapper -> wrapper.eq("node_name", nodeName));
    }

    public PrintNodeEntity createNode(PrintNodeEntity entity) {
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        return MapperUtils.insert(printNodeMapper, entity);
    }

    public PrintNodeEntity updateNode(PrintNodeEntity entity) {
        entity.setUpdatedAt(LocalDateTime.now());
        return MapperUtils.updateById(printNodeMapper, entity);
    }

    public void deleteNode(String id) {
        MapperUtils.deleteById(printNodeMapper, id);
    }
}
