package com.jnimble.plugin.order.model.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class KitchenQueueModels {

    private KitchenQueueModels() {
    }

    public record ItemView(
            String id,
            String orderId,
            String orderItemId,
            String menuItemId,
            String orderNo,
            String number,
            String itemName,
            String specification,
            String remark,
            Integer quantity,
            Integer orderBatchSeq,
            String productionBatchNo,
            String dispatchMode,
            String status,
            String printJobId,
            LocalDateTime confirmedAt,
            LocalDateTime startedAt,
            LocalDateTime completedAt,
            LocalDateTime printedAt
    ) {
    }

    public record GroupView(
            String key,
            String itemName,
            String specification,
            Integer totalQuantity,
            Integer orderCount,
            LocalDateTime earliestConfirmedAt,
            List<ItemView> items
    ) {
    }

    public record QueueResponse(
            boolean grouped,
            List<ItemView> items,
            List<GroupView> groups,
            Map<String, Long> statusCounts,
            boolean paged,
            int page,
            int pageSize,
            long total,
            long totalPages
    ) {
    }

    public record BatchRequest(List<String> queueItemIds) {
    }

    public record ProductionBatchRequest(List<String> productionBatchNos) {
    }

    public record PrintResponse(String jobId, int itemCount) {
    }
}
