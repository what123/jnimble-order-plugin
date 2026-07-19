package com.jnimble.plugin.order.table.service;

import com.jnimble.platform.persistence.crud.MapperUtils;
import com.jnimble.plugin.order.table.mapper.TableMapper;
import com.jnimble.plugin.order.table.model.entity.TableEntity;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TableService {

    private final TableMapper tableMapper;

    public TableService(TableMapper tableMapper) {
        this.tableMapper = tableMapper;
    }

    public List<TableEntity> listTables(String area) {
        return MapperUtils.selectList(tableMapper, TableEntity.class, wrapper -> {
            if (area != null && !area.isBlank()) {
                wrapper.eq("area", area);
            }
            wrapper.orderByAsc("code");
        });
    }

    public TableEntity getTable(Long id) {
        return MapperUtils.getById(tableMapper, id, "Table not found: " + id);
    }

    public TableEntity getTableForUpdate(Long id) {
        TableEntity table = tableMapper.selectByIdForUpdate(id);
        if (table == null) {
            throw new IllegalArgumentException("Table not found: " + id);
        }
        return table;
    }

    @Transactional
    public TableEntity createTable(TableEntity entity) {
        normalizeTableConfiguration(entity);
        LocalDateTime now = LocalDateTime.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        if (entity.getStatus() == null) {
            entity.setStatus("FREE");
        }
        if (entity.getOperationalStatus() == null) {
            entity.setOperationalStatus("AVAILABLE");
        }
        if (entity.getVersion() == null) {
            entity.setVersion(0);
        }
        // scan_code is non-null and unique. Use a transaction-local placeholder until the generated ID is available.
        entity.setScanCode(createTemporaryScanCode());
        MapperUtils.insert(tableMapper, entity);
        if (entity.getId() == null) {
            throw new IllegalStateException("Unable to generate table scan code without a table ID");
        }

        entity.setScanCode(formatScanCode(entity.getId()));
        entity.setUpdatedAt(LocalDateTime.now());
        MapperUtils.updateById(tableMapper, entity);
        return entity;
    }

    public TableEntity updateTable(TableEntity entity) {
        entity.setUpdatedAt(LocalDateTime.now());
        return MapperUtils.updateById(tableMapper, entity);
    }

    public void deleteTable(Long id) {
        MapperUtils.deleteById(tableMapper, id);
    }

    public TableEntity updateStatus(Long id, String status) {
        TableEntity entity = new TableEntity();
        entity.setId(id);
        entity.setStatus(status);
        entity.setUpdatedAt(LocalDateTime.now());
        return MapperUtils.updateById(tableMapper, entity);
    }

    public TableEntity updateRuntimeStatus(Long id, String displayStatus, String operationalStatus) {
        TableEntity entity = new TableEntity();
        entity.setId(id);
        entity.setStatus(displayStatus);
        entity.setOperationalStatus(operationalStatus);
        entity.setUpdatedAt(LocalDateTime.now());
        return MapperUtils.updateById(tableMapper, entity);
    }

    public TableEntity completeTurnover(Long id) {
        TableEntity entity = new TableEntity();
        entity.setId(id);
        entity.setStatus("FREE");
        entity.setOperationalStatus("AVAILABLE");
        entity.setLastTurnedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        return MapperUtils.updateById(tableMapper, entity);
    }

    public void validatePartySize(TableEntity table, Integer partySize) {
        if (partySize == null || partySize < 1) {
            throw new IllegalArgumentException("Party size must be at least one");
        }

        int minimum = table.getMinPartySize() == null ? 1 : table.getMinPartySize();
        Integer maximum = table.getMaxPartySize() != null ? table.getMaxPartySize() : table.getSeatCount();
        if (partySize < minimum || (maximum != null && partySize > maximum)) {
            throw new IllegalArgumentException("Party size is outside the table capacity range");
        }
    }

    private void normalizeTableConfiguration(TableEntity entity) {
        if (entity == null) {
            throw new IllegalArgumentException("Table is required");
        }

        String tableName = trimToNull(entity.getTableName());
        if (tableName == null) {
            tableName = trimToNull(entity.getCode());
        }
        if (tableName == null) {
            throw new IllegalArgumentException("Table name is required");
        }

        int minPartySize = entity.getMinPartySize() == null ? 1 : entity.getMinPartySize();
        int maxPartySize = entity.getMaxPartySize() == null
                ? (entity.getSeatCount() == null ? 4 : entity.getSeatCount())
                : entity.getMaxPartySize();
        if (minPartySize < 1) {
            throw new IllegalArgumentException("Minimum party size must be at least one");
        }
        if (maxPartySize < minPartySize) {
            throw new IllegalArgumentException("Maximum party size must not be smaller than the minimum");
        }

        entity.setTableName(tableName);
        if (trimToNull(entity.getCode()) == null) {
            entity.setCode(tableName);
        }
        entity.setMinPartySize(minPartySize);
        entity.setMaxPartySize(maxPartySize);
        // Keep the legacy capacity field aligned for POS callers that still read it.
        entity.setSeatCount(maxPartySize);
    }

    private String formatScanCode(Long tableId) {
        return String.format(Locale.ROOT, "TB%08d", tableId);
    }

    private String createTemporaryScanCode() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
