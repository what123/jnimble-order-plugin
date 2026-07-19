package com.jnimble.plugin.order.table.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("ord_table_occupancy")
public class TableOccupancyEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tableId;
    private Long sessionId;
    private String mode;
    private Integer seatCount;
    private String status;
    private LocalDateTime occupiedAt;
    private LocalDateTime releasedAt;
    private String releaseReason;
    private Long fromOccupancyId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTableId() { return tableId; }
    public void setTableId(Long tableId) { this.tableId = tableId; }
    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public Integer getSeatCount() { return seatCount; }
    public void setSeatCount(Integer seatCount) { this.seatCount = seatCount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getOccupiedAt() { return occupiedAt; }
    public void setOccupiedAt(LocalDateTime occupiedAt) { this.occupiedAt = occupiedAt; }
    public LocalDateTime getReleasedAt() { return releasedAt; }
    public void setReleasedAt(LocalDateTime releasedAt) { this.releasedAt = releasedAt; }
    public String getReleaseReason() { return releaseReason; }
    public void setReleaseReason(String releaseReason) { this.releaseReason = releaseReason; }
    public Long getFromOccupancyId() { return fromOccupancyId; }
    public void setFromOccupancyId(Long fromOccupancyId) { this.fromOccupancyId = fromOccupancyId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
