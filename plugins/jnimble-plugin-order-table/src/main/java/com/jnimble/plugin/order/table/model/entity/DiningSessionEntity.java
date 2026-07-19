package com.jnimble.plugin.order.table.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("ord_dining_session")
public class DiningSessionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String sessionNo;
    private Integer guestCount;
    private String status;
    private Long primaryTableId;
    private Long reservationId;
    private String customerRef;
    private LocalDateTime holdExpiresAt;
    private String openedBy;
    private LocalDateTime openedAt;
    private LocalDateTime checkingAt;
    private LocalDateTime paidAt;
    private LocalDateTime closedAt;
    private Integer version;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSessionNo() { return sessionNo; }
    public void setSessionNo(String sessionNo) { this.sessionNo = sessionNo; }
    public Integer getGuestCount() { return guestCount; }
    public void setGuestCount(Integer guestCount) { this.guestCount = guestCount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getPrimaryTableId() { return primaryTableId; }
    public void setPrimaryTableId(Long primaryTableId) { this.primaryTableId = primaryTableId; }
    public Long getReservationId() { return reservationId; }
    public void setReservationId(Long reservationId) { this.reservationId = reservationId; }
    public String getCustomerRef() { return customerRef; }
    public void setCustomerRef(String customerRef) { this.customerRef = customerRef; }
    public LocalDateTime getHoldExpiresAt() { return holdExpiresAt; }
    public void setHoldExpiresAt(LocalDateTime holdExpiresAt) { this.holdExpiresAt = holdExpiresAt; }
    public String getOpenedBy() { return openedBy; }
    public void setOpenedBy(String openedBy) { this.openedBy = openedBy; }
    public LocalDateTime getOpenedAt() { return openedAt; }
    public void setOpenedAt(LocalDateTime openedAt) { this.openedAt = openedAt; }
    public LocalDateTime getCheckingAt() { return checkingAt; }
    public void setCheckingAt(LocalDateTime checkingAt) { this.checkingAt = checkingAt; }
    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(LocalDateTime closedAt) { this.closedAt = closedAt; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
