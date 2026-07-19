package com.jnimble.plugin.order.service;

import com.jnimble.platform.persistence.crud.MapperUtils;
import com.jnimble.plugin.order.kitchen.KitchenDispatchMode;
import com.jnimble.plugin.order.kitchen.KitchenDispatchModeRegistry;
import com.jnimble.plugin.order.kitchen.KitchenPrintGatewayRegistry;
import com.jnimble.plugin.order.kitchen.KitchenPrintRequest;
import com.jnimble.plugin.order.kitchen.KitchenPrintResult;
import com.jnimble.plugin.order.kitchen.KitchenQueueStatus;
import com.jnimble.plugin.order.kitchen.KitchenTicketNumberContext;
import com.jnimble.plugin.order.kitchen.KitchenTicketNumberRegistry;
import com.jnimble.plugin.order.mapper.KitchenQueueItemMapper;
import com.jnimble.plugin.order.mapper.OrderMapper;
import com.jnimble.plugin.order.model.dto.KitchenQueueModels.GroupView;
import com.jnimble.plugin.order.model.dto.KitchenQueueModels.ItemView;
import com.jnimble.plugin.order.model.dto.KitchenQueueModels.PrintResponse;
import com.jnimble.plugin.order.model.dto.KitchenQueueModels.QueueResponse;
import com.jnimble.plugin.order.model.entity.KitchenQueueItemEntity;
import com.jnimble.plugin.order.model.entity.OrderEntity;
import com.jnimble.plugin.order.model.entity.OrderItemEntity;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KitchenQueueService {

    private static final DateTimeFormatter PRODUCTION_BATCH_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final int HISTORY_PAGE_SIZE = 50;

    private final KitchenQueueItemMapper queueItemMapper;
    private final OrderMapper orderMapper;
    private final KitchenDispatchModeRegistry dispatchModeRegistry;
    private final KitchenTicketNumberRegistry ticketNumberRegistry;
    private final KitchenPrintGatewayRegistry printGatewayRegistry;

    public KitchenQueueService(
            KitchenQueueItemMapper queueItemMapper,
            OrderMapper orderMapper,
            KitchenDispatchModeRegistry dispatchModeRegistry,
            KitchenTicketNumberRegistry ticketNumberRegistry,
            KitchenPrintGatewayRegistry printGatewayRegistry
    ) {
        this.queueItemMapper = queueItemMapper;
        this.orderMapper = orderMapper;
        this.dispatchModeRegistry = dispatchModeRegistry;
        this.ticketNumberRegistry = ticketNumberRegistry;
        this.printGatewayRegistry = printGatewayRegistry;
    }

    public void enqueue(OrderEntity order, List<OrderItemEntity> orderItems) {
        if (order == null || order.getId() == null) {
            throw new IllegalArgumentException("Order is required for kitchen dispatch");
        }
        if (orderItems == null || orderItems.isEmpty()) {
            return;
        }

        Set<Long> existingItemIds = MapperUtils.selectList(
                        queueItemMapper,
                        KitchenQueueItemEntity.class,
                        wrapper -> wrapper.eq("order_id", order.getId())
                ).stream()
                .map(KitchenQueueItemEntity::getOrderItemId)
                .collect(Collectors.toSet());
        KitchenTicketNumberContext context = context(order);
        KitchenDispatchMode dispatchMode = dispatchModeRegistry.resolve(context);
        LocalDateTime now = LocalDateTime.now();

        for (OrderItemEntity orderItem : orderItems) {
            if (orderItem.getId() == null || existingItemIds.contains(orderItem.getId())) {
                continue;
            }
            KitchenQueueItemEntity queueItem = new KitchenQueueItemEntity();
            queueItem.setOrderId(order.getId());
            queueItem.setOrderItemId(orderItem.getId());
            queueItem.setMenuItemId(orderItem.getMenuItemId());
            queueItem.setTableId(order.getTableId());
            queueItem.setSessionId(order.getSessionId());
            queueItem.setOrderNo(order.getOrderNo());
            queueItem.setSource(order.getSource() == null ? "LEGACY" : order.getSource());
            queueItem.setItemName(orderItem.getItemName());
            queueItem.setSpecification(orderItem.getSpecification());
            queueItem.setRemark(orderItem.getRemark());
            queueItem.setQuantity(orderItem.getQuantity());
            queueItem.setOrderBatchSeq(orderItem.getBatchSeq());
            queueItem.setDispatchMode(dispatchMode.name());
            queueItem.setStatus(KitchenQueueStatus.WAITING.name());
            queueItem.setConfirmedAt(now);
            queueItem.setCreatedAt(now);
            queueItem.setUpdatedAt(now);
            MapperUtils.insert(queueItemMapper, queueItem);
        }
    }

    @Transactional(readOnly = true)
    public QueueResponse list(String statusValue, String dispatchModeValue, boolean grouped) {
        return list(statusValue, dispatchModeValue, grouped, 1);
    }

    @Transactional(readOnly = true)
    public QueueResponse list(String statusValue, String dispatchModeValue, boolean grouped, int pageValue) {
        KitchenQueueStatus status = parseStatus(statusValue);
        KitchenDispatchMode dispatchMode = parseDispatchMode(dispatchModeValue);
        if (grouped && (dispatchMode != KitchenDispatchMode.PAPERLESS
                || status != KitchenQueueStatus.WAITING)) {
            throw new IllegalArgumentException("只有无纸模式的待制作菜品可以同菜合并");
        }

        boolean paged = status == KitchenQueueStatus.COMPLETED || status == KitchenQueueStatus.PRINTED;
        long total = paged ? queueItemMapper.selectCount(MapperUtils.buildWrapper(
                KitchenQueueItemEntity.class,
                wrapper -> wrapper.eq("status", status.name()).eq("dispatch_mode", dispatchMode.name()))) : 0;
        long totalPages = paged ? Math.max(1, (total + HISTORY_PAGE_SIZE - 1) / HISTORY_PAGE_SIZE) : 1;
        int page = paged ? (int) Math.min(Math.max(1, pageValue), totalPages) : 1;
        List<KitchenQueueItemEntity> entities = MapperUtils.selectList(
                queueItemMapper,
                KitchenQueueItemEntity.class,
                wrapper -> {
                    wrapper.eq("status", status.name()).eq("dispatch_mode", dispatchMode.name());
                    if (paged) {
                        wrapper.orderByDesc(status == KitchenQueueStatus.COMPLETED ? "completed_at" : "printed_at")
                                .orderByDesc("id")
                                .last("limit " + HISTORY_PAGE_SIZE + " offset " + ((page - 1) * HISTORY_PAGE_SIZE));
                    } else {
                        wrapper.orderByAsc("confirmed_at").orderByAsc("id");
                    }
                }
        );
        Map<Long, String> numbers = new LinkedHashMap<>();
        List<ItemView> items = entities.stream()
                .map(entity -> toView(entity, numbers.computeIfAbsent(
                        entity.getOrderId(),
                        ignored -> ticketNumberRegistry.resolve(context(entity))
                )))
                .toList();
        List<GroupView> groups = grouped ? group(items) : List.of();
        return new QueueResponse(
                grouped,
                grouped ? List.of() : items,
                groups,
                statusCounts(dispatchMode),
                paged,
                page,
                HISTORY_PAGE_SIZE,
                total,
                totalPages);
    }

    @Transactional
    public void start(Long queueItemId) {
        KitchenQueueItemEntity item = requireQueueItem(queueItemId);
        requirePaperlessWaiting(item);
        LocalDateTime now = LocalDateTime.now();
        if (queueItemMapper.start(queueItemId, newProductionBatchNo(now), now) != 1) {
            throw new IllegalStateException("菜品状态已变化，请刷新制作队列");
        }
    }

    @Transactional
    public void startGroup(List<String> queueItemIds) {
        List<Long> ids = parseIds(queueItemIds);
        List<KitchenQueueItemEntity> items = ids.stream().map(this::requireQueueItem).toList();
        items.forEach(this::requirePaperlessWaiting);
        long groupCount = items.stream().map(this::groupKey).distinct().count();
        if (groupCount != 1) {
            throw new IllegalArgumentException("整组开始只能包含同一菜品和规格");
        }
        LocalDateTime now = LocalDateTime.now();
        String productionBatchNo = newProductionBatchNo(now);
        for (KitchenQueueItemEntity item : items) {
            if (queueItemMapper.start(item.getId(), productionBatchNo, now) != 1) {
                throw new IllegalStateException("菜品状态已变化，请刷新制作队列");
            }
        }
    }

    @Transactional
    public void complete(Long queueItemId) {
        KitchenQueueItemEntity item = requireQueueItem(queueItemId);
        requirePaperlessCooking(item);
        completeProductionBatches(List.of(item.getProductionBatchNo()));
    }

    @Transactional
    public void completeProductionBatches(List<String> productionBatchNos) {
        List<String> batchNos = normalizeProductionBatchNos(productionBatchNos);
        LocalDateTime now = LocalDateTime.now();
        for (String batchNo : batchNos) {
            List<KitchenQueueItemEntity> items = MapperUtils.selectList(
                    queueItemMapper,
                    KitchenQueueItemEntity.class,
                    wrapper -> wrapper.eq("production_batch_no", batchNo).orderByAsc("id")
            );
            if (items.isEmpty()) {
                throw new IllegalArgumentException("制作批次不存在：" + batchNo);
            }
            items.forEach(this::requirePaperlessCooking);
            if (queueItemMapper.completeProductionBatch(batchNo, now) != items.size()) {
                throw new IllegalStateException("菜品状态已变化，请刷新制作队列");
            }
        }
    }

    @Transactional
    public PrintResponse printOrder(Long orderId) {
        List<KitchenQueueItemEntity> waitingItems = queueItems(orderId, KitchenQueueStatus.WAITING);
        if (waitingItems.isEmpty()) {
            List<KitchenQueueItemEntity> printedItems = queueItems(orderId, KitchenQueueStatus.PRINTED);
            if (!printedItems.isEmpty()) {
                return new PrintResponse(printedItems.getFirst().getPrintJobId(), printedItems.size());
            }
            throw new IllegalStateException("该订单没有待打印的后厨菜品");
        }
        if (waitingItems.stream().anyMatch(item -> !KitchenDispatchMode.PRINT.name().equals(item.getDispatchMode()))) {
            throw new IllegalStateException("无纸模式菜品不能创建纸质后厨单");
        }

        OrderEntity order = MapperUtils.getById(orderMapper, orderId, "Order not found: " + orderId);
        String ticketNumber = ticketNumberRegistry.resolve(context(waitingItems.getFirst()));
        KitchenPrintResult result = printGatewayRegistry.print(
                new KitchenPrintRequest(order, waitingItems, ticketNumber)
        );
        if (result == null || result.jobId() == null || result.jobId().isBlank()) {
            throw new IllegalStateException("打印插件未返回有效的打印任务编号");
        }
        LocalDateTime now = LocalDateTime.now();
        for (KitchenQueueItemEntity item : waitingItems) {
            if (queueItemMapper.markPrinted(item.getId(), result.jobId(), now) != 1) {
                throw new IllegalStateException("菜品状态已变化，请刷新制作队列");
            }
        }
        return new PrintResponse(result.jobId(), waitingItems.size());
    }

    private List<KitchenQueueItemEntity> queueItems(Long orderId, KitchenQueueStatus status) {
        return MapperUtils.selectList(
                queueItemMapper,
                KitchenQueueItemEntity.class,
                wrapper -> wrapper.eq("order_id", orderId)
                        .eq("status", status.name())
                        .orderByAsc("id")
        );
    }

    private Map<String, Long> statusCounts(KitchenDispatchMode dispatchMode) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (KitchenQueueStatus status : KitchenQueueStatus.values()) {
            counts.put(status.name(), queueItemMapper.selectCount(MapperUtils.buildWrapper(
                    KitchenQueueItemEntity.class,
                    wrapper -> wrapper.eq("dispatch_mode", dispatchMode.name()).eq("status", status.name()))));
        }
        return counts;
    }

    private List<GroupView> group(List<ItemView> items) {
        Map<String, List<ItemView>> groupedItems = new LinkedHashMap<>();
        for (ItemView item : items) {
            String key = groupKey(item.menuItemId(), item.itemName(), item.specification());
            groupedItems.computeIfAbsent(key, ignored -> new ArrayList<>()).add(item);
        }
        List<GroupView> result = new ArrayList<>();
        for (Map.Entry<String, List<ItemView>> entry : groupedItems.entrySet()) {
            List<ItemView> groupItems = entry.getValue();
            ItemView first = groupItems.getFirst();
            int totalQuantity = groupItems.stream()
                    .map(ItemView::quantity)
                    .filter(Objects::nonNull)
                    .reduce(0, Integer::sum);
            int orderCount = new LinkedHashSet<>(groupItems.stream().map(ItemView::orderId).toList()).size();
            result.add(new GroupView(
                    entry.getKey(),
                    first.itemName(),
                    first.specification(),
                    totalQuantity,
                    orderCount,
                    first.confirmedAt(),
                    List.copyOf(groupItems)
            ));
        }
        return List.copyOf(result);
    }

    private ItemView toView(KitchenQueueItemEntity item, String number) {
        return new ItemView(
                stringValue(item.getId()),
                stringValue(item.getOrderId()),
                stringValue(item.getOrderItemId()),
                stringValue(item.getMenuItemId()),
                item.getOrderNo(),
                number,
                item.getItemName(),
                item.getSpecification(),
                item.getRemark(),
                item.getQuantity(),
                item.getOrderBatchSeq(),
                item.getProductionBatchNo(),
                item.getDispatchMode(),
                item.getStatus(),
                item.getPrintJobId(),
                item.getConfirmedAt(),
                item.getStartedAt(),
                item.getCompletedAt(),
                item.getPrintedAt()
        );
    }

    private KitchenQueueItemEntity requireQueueItem(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("制作项 ID 不能为空");
        }
        return MapperUtils.getById(queueItemMapper, id, "Kitchen queue item not found: " + id);
    }

    private void requirePaperlessWaiting(KitchenQueueItemEntity item) {
        if (!KitchenDispatchMode.PAPERLESS.name().equals(item.getDispatchMode())
                || !KitchenQueueStatus.WAITING.name().equals(item.getStatus())) {
            throw new IllegalStateException("只有无纸模式的待制作菜品可以开始制作");
        }
    }

    private void requirePaperlessCooking(KitchenQueueItemEntity item) {
        if (!KitchenDispatchMode.PAPERLESS.name().equals(item.getDispatchMode())
                || !KitchenQueueStatus.COOKING.name().equals(item.getStatus())
                || item.getProductionBatchNo() == null
                || item.getProductionBatchNo().isBlank()) {
            throw new IllegalStateException("只有带制作批次的无纸模式制作中菜品可以完成");
        }
    }

    private List<Long> parseIds(List<String> values) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("制作项不能为空");
        }
        Set<Long> ids = new LinkedHashSet<>();
        for (String value : values) {
            try {
                ids.add(Long.valueOf(value));
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("制作项 ID 格式不正确");
            }
        }
        return List.copyOf(ids);
    }

    private List<String> normalizeProductionBatchNos(List<String> values) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("制作批次不能为空");
        }
        Set<String> batchNos = new LinkedHashSet<>();
        for (String value : values) {
            String batchNo = value == null ? "" : value.trim();
            if (batchNo.isEmpty() || batchNo.length() > 64) {
                throw new IllegalArgumentException("制作批次号格式不正确");
            }
            batchNos.add(batchNo);
        }
        return List.copyOf(batchNos);
    }

    private String newProductionBatchNo(LocalDateTime now) {
        return "PB" + PRODUCTION_BATCH_TIME.format(now) + "-"
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private KitchenQueueStatus parseStatus(String value) {
        try {
            return KitchenQueueStatus.valueOf(normalize(value, KitchenQueueStatus.WAITING.name()));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("不支持的制作状态：" + value);
        }
    }

    private KitchenDispatchMode parseDispatchMode(String value) {
        try {
            return KitchenDispatchMode.valueOf(normalize(value, KitchenDispatchMode.PAPERLESS.name()));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("不支持的后厨作业方式：" + value);
        }
    }

    private String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim().toUpperCase(Locale.ROOT);
    }

    private String groupKey(KitchenQueueItemEntity item) {
        return groupKey(stringValue(item.getMenuItemId()), item.getItemName(), item.getSpecification());
    }

    private String groupKey(String menuItemId, String itemName, String specification) {
        String identity = menuItemId == null
                ? "name:" + normalizedPart(itemName)
                : "id:" + menuItemId;
        return identity + "|spec:" + normalizedPart(specification);
    }

    private String normalizedPart(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private KitchenTicketNumberContext context(OrderEntity order) {
        return new KitchenTicketNumberContext(
                order.getId(), order.getOrderNo(), order.getTableId(), order.getSessionId(), order.getSource()
        );
    }

    private KitchenTicketNumberContext context(KitchenQueueItemEntity item) {
        return new KitchenTicketNumberContext(
                item.getOrderId(), item.getOrderNo(), item.getTableId(), item.getSessionId(), item.getSource()
        );
    }

    private String stringValue(Long value) {
        return value == null ? null : String.valueOf(value);
    }
}
