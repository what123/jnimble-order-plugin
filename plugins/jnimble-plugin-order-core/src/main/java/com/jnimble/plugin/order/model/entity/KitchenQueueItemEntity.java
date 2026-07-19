package com.jnimble.plugin.order.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.time.LocalDateTime;

@TableName("ord_kitchen_queue_item")
public class KitchenQueueItemEntity {

    @TableId(type = IdType.AUTO)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long orderId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long orderItemId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long menuItemId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long tableId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long sessionId;
    private String orderNo;
    private String source;
    private String itemName;
    private String specification;
    private String remark;
    private Integer quantity;
    private Integer orderBatchSeq;
    private String productionBatchNo;
    private String dispatchMode;
    private String status;
    private String printJobId;
    private LocalDateTime confirmedAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime printedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getOrderItemId() { return orderItemId; }
    public void setOrderItemId(Long orderItemId) { this.orderItemId = orderItemId; }
    public Long getMenuItemId() { return menuItemId; }
    public void setMenuItemId(Long menuItemId) { this.menuItemId = menuItemId; }
    public Long getTableId() { return tableId; }
    public void setTableId(Long tableId) { this.tableId = tableId; }
    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }
    public String getSpecification() { return specification; }
    public void setSpecification(String specification) { this.specification = specification; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public Integer getOrderBatchSeq() { return orderBatchSeq; }
    public void setOrderBatchSeq(Integer orderBatchSeq) { this.orderBatchSeq = orderBatchSeq; }
    public String getProductionBatchNo() { return productionBatchNo; }
    public void setProductionBatchNo(String productionBatchNo) { this.productionBatchNo = productionBatchNo; }
    public String getDispatchMode() { return dispatchMode; }
    public void setDispatchMode(String dispatchMode) { this.dispatchMode = dispatchMode; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPrintJobId() { return printJobId; }
    public void setPrintJobId(String printJobId) { this.printJobId = printJobId; }
    public LocalDateTime getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(LocalDateTime confirmedAt) { this.confirmedAt = confirmedAt; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public LocalDateTime getPrintedAt() { return printedAt; }
    public void setPrintedAt(LocalDateTime printedAt) { this.printedAt = printedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
