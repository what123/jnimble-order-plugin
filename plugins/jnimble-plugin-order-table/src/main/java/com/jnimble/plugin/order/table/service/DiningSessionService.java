package com.jnimble.plugin.order.table.service;

import com.jnimble.platform.persistence.crud.MapperUtils;
import com.jnimble.plugin.order.model.entity.OrderEntity;
import com.jnimble.plugin.order.payment.OrderPaymentResult;
import com.jnimble.plugin.order.service.OrderService;
import com.jnimble.plugin.order.table.mapper.CheckoutMapper;
import com.jnimble.plugin.order.table.mapper.DiningSessionMapper;
import com.jnimble.plugin.order.table.mapper.TableOccupancyMapper;
import com.jnimble.plugin.order.table.mapper.TableOperationLogMapper;
import com.jnimble.plugin.order.table.model.dto.PosModels.CheckoutResult;
import com.jnimble.plugin.order.table.model.dto.PosModels.SessionDetail;
import com.jnimble.plugin.order.table.model.dto.PosModels.SessionSummary;
import com.jnimble.plugin.order.table.model.dto.PosModels.TableBoardItem;
import com.jnimble.plugin.order.table.model.entity.CheckoutEntity;
import com.jnimble.plugin.order.table.model.entity.DiningSessionEntity;
import com.jnimble.plugin.order.table.model.entity.TableEntity;
import com.jnimble.plugin.order.table.model.entity.TableOccupancyEntity;
import com.jnimble.plugin.order.table.model.entity.TableOperationLogEntity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DiningSessionService {

    private final TableService tableService;
    private final OrderService orderService;
    private final DiningSessionMapper sessionMapper;
    private final TableOccupancyMapper occupancyMapper;
    private final CheckoutMapper checkoutMapper;
    private final TableOperationLogMapper operationLogMapper;

    public DiningSessionService(TableService tableService,
                                OrderService orderService,
                                DiningSessionMapper sessionMapper,
                                TableOccupancyMapper occupancyMapper,
                                CheckoutMapper checkoutMapper,
                                TableOperationLogMapper operationLogMapper) {
        this.tableService = tableService;
        this.orderService = orderService;
        this.sessionMapper = sessionMapper;
        this.occupancyMapper = occupancyMapper;
        this.checkoutMapper = checkoutMapper;
        this.operationLogMapper = operationLogMapper;
    }

    public List<TableBoardItem> board() {
        List<TableBoardItem> board = new ArrayList<>();
        for (TableEntity table : tableService.listTables(null)) {
            List<TableOccupancyEntity> occupancies = activeOccupanciesForTable(table.getId());
            List<SessionSummary> sessions = occupancies.stream()
                    .map(TableOccupancyEntity::getSessionId)
                    .distinct()
                    .map(this::sessionSummary)
                    .filter(summary -> summary != null)
                    .toList();
            int occupiedSeats = occupancies.stream()
                    .map(TableOccupancyEntity::getSeatCount)
                    .filter(value -> value != null)
                    .reduce(0, Integer::sum);
            int maximum = table.getMaxPartySize() == null
                    ? (table.getSeatCount() == null ? 0 : table.getSeatCount())
                    : table.getMaxPartySize();
            board.add(new TableBoardItem(
                    table.getId(),
                    table.getTableName() == null ? table.getCode() : table.getTableName(),
                    table.getArea(),
                    table.getMinPartySize(),
                    maximum,
                    table.getScanCode(),
                    deriveStatus(table, sessions),
                    occupiedSeats,
                    Math.max(0, maximum - occupiedSeats),
                    sessions
            ));
        }
        return board;
    }

    public SessionDetail getDetail(Long sessionId) {
        DiningSessionEntity session = getSession(sessionId);
        List<TableOccupancyEntity> occupancies = activeOccupanciesForSession(sessionId);
        List<TableEntity> tables = occupancies.stream()
                .map(TableOccupancyEntity::getTableId)
                .distinct()
                .map(tableService::getTable)
                .toList();
        OrderEntity order = orderService.findActiveOrderBySessionId(sessionId);
        return new SessionDetail(
                session,
                tableService.getTable(session.getPrimaryTableId()),
                tables,
                order,
                order == null ? List.of() : orderService.getActiveOrderItems(order.getId())
        );
    }

    @Transactional
    public SessionDetail openTable(Long tableId, Integer guestCount, String operator,
                                   String remark, boolean shared) {
        TableEntity table = tableService.getTableForUpdate(tableId);
        requireOperational(table);
        tableService.validatePartySize(table, guestCount);
        List<TableOccupancyEntity> existing = activeOccupanciesForTable(tableId);
        if (!shared && !existing.isEmpty()) {
            throw new IllegalStateException("Table is already occupied");
        }
        int occupiedSeats = existing.stream()
                .map(TableOccupancyEntity::getSeatCount)
                .filter(value -> value != null)
                .reduce(0, Integer::sum);
        Integer maximum = table.getMaxPartySize() == null ? table.getSeatCount() : table.getMaxPartySize();
        if (maximum != null && occupiedSeats + guestCount > maximum) {
            throw new IllegalArgumentException("Guest count exceeds the remaining table capacity");
        }

        LocalDateTime now = LocalDateTime.now();
        DiningSessionEntity session = new DiningSessionEntity();
        session.setSessionNo(newSessionNo());
        session.setGuestCount(guestCount);
        session.setStatus("DINING");
        session.setPrimaryTableId(tableId);
        session.setOpenedBy(normalizeOperator(operator));
        session.setOpenedAt(now);
        session.setVersion(0);
        session.setRemark(trimToNull(remark));
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        MapperUtils.insert(sessionMapper, session);

        orderService.createOrder(tableId, guestCount, normalizeOperator(operator),
                session.getId(), "POS");
        insertOccupancy(tableId, session.getId(), existing.isEmpty() ? "PRIMARY" : "SHARED",
                guestCount, null);
        tableService.updateRuntimeStatus(tableId, "OCCUPIED", "AVAILABLE");
        log(session.getId(), tableId, shared ? "SHARE_OPEN" : "OPEN",
                null, "guestCount=" + guestCount, null, operator);
        return getDetail(session.getId());
    }

    @Transactional
    public SessionDetail updateGuestCount(Long sessionId, Integer guestCount, String operator) {
        DiningSessionEntity session = requireDiningSession(sessionId);
        TableEntity table = tableService.getTableForUpdate(session.getPrimaryTableId());
        tableService.validatePartySize(table, guestCount);
        List<TableOccupancyEntity> occupancies = activeOccupanciesForTable(table.getId());
        int occupiedByOthers = occupancies.stream()
                .filter(occupancy -> !sessionId.equals(occupancy.getSessionId()))
                .map(TableOccupancyEntity::getSeatCount)
                .filter(value -> value != null)
                .reduce(0, Integer::sum);
        Integer maximum = table.getMaxPartySize() == null ? table.getSeatCount() : table.getMaxPartySize();
        if (maximum != null && occupiedByOthers + guestCount > maximum) {
            throw new IllegalArgumentException("Guest count exceeds the remaining table capacity");
        }

        int previous = session.getGuestCount();
        DiningSessionEntity update = new DiningSessionEntity();
        update.setId(sessionId);
        update.setGuestCount(guestCount);
        update.setUpdatedAt(LocalDateTime.now());
        MapperUtils.updateById(sessionMapper, update);
        occupancies.stream()
                .filter(occupancy -> sessionId.equals(occupancy.getSessionId())
                        && table.getId().equals(occupancy.getTableId()))
                .findFirst()
                .ifPresent(occupancy -> updateOccupancySeats(occupancy, guestCount));
        OrderEntity order = requireActiveOrder(sessionId);
        orderService.updatePartySize(order.getId(), guestCount);
        log(sessionId, table.getId(), "GUEST_COUNT", String.valueOf(previous),
                String.valueOf(guestCount), null, operator);
        return getDetail(sessionId);
    }

    @Transactional
    public SessionDetail combineTable(Long sessionId, Long targetTableId, String operator) {
        DiningSessionEntity session = requireDiningSession(sessionId);
        lockTables(session.getPrimaryTableId(), targetTableId);
        TableEntity target = tableService.getTable(targetTableId);
        requireOperational(target);
        if (!activeOccupanciesForTable(targetTableId).isEmpty()) {
            throw new IllegalStateException("Target table is already occupied");
        }
        if (activeOccupanciesForSession(sessionId).stream()
                .anyMatch(occupancy -> targetTableId.equals(occupancy.getTableId()))) {
            return getDetail(sessionId);
        }
        insertOccupancy(targetTableId, sessionId, "COMBINED", 0, null);
        tableService.updateRuntimeStatus(targetTableId, "OCCUPIED", "AVAILABLE");
        log(sessionId, targetTableId, "COMBINE", null,
                "primaryTableId=" + session.getPrimaryTableId(), null, operator);
        return getDetail(sessionId);
    }

    @Transactional
    public SessionDetail transferTable(Long sessionId, Long targetTableId, String operator) {
        DiningSessionEntity session = requireDiningSession(sessionId);
        Long sourceTableId = session.getPrimaryTableId();
        if (sourceTableId.equals(targetTableId)) {
            return getDetail(sessionId);
        }
        lockTables(sourceTableId, targetTableId);
        TableEntity target = tableService.getTable(targetTableId);
        requireOperational(target);
        if (!activeOccupanciesForTable(targetTableId).isEmpty()) {
            throw new IllegalStateException("Target table is already occupied");
        }
        TableOccupancyEntity source = activeOccupanciesForSession(sessionId).stream()
                .filter(occupancy -> sourceTableId.equals(occupancy.getTableId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Primary table occupancy was not found"));
        releaseOccupancy(source, "TRANSFER");
        insertOccupancy(targetTableId, sessionId, "PRIMARY", session.getGuestCount(), source.getId());

        DiningSessionEntity update = new DiningSessionEntity();
        update.setId(sessionId);
        update.setPrimaryTableId(targetTableId);
        update.setUpdatedAt(LocalDateTime.now());
        MapperUtils.updateById(sessionMapper, update);
        orderService.updateTableAssociation(requireActiveOrder(sessionId).getId(), targetTableId);
        refreshReleasedTableStatus(sourceTableId);
        tableService.updateRuntimeStatus(targetTableId, "OCCUPIED", "AVAILABLE");
        log(sessionId, targetTableId, "TRANSFER", String.valueOf(sourceTableId),
                String.valueOf(targetTableId), null, operator);
        return getDetail(sessionId);
    }

    @Transactional
    public void completeTurnover(Long tableId, String operator) {
        TableEntity table = tableService.getTableForUpdate(tableId);
        if (!activeOccupanciesForTable(tableId).isEmpty()) {
            throw new IllegalStateException("Table still has active dining sessions");
        }
        if ("FREE".equals(table.getStatus())) {
            return;
        }
        if (!"CLEANING".equals(table.getStatus())) {
            throw new IllegalStateException("Table is not waiting for cleaning");
        }
        tableService.completeTurnover(tableId);
        log(null, tableId, "TURNOVER_COMPLETE", "CLEANING", "FREE", null, operator);
    }

    @Transactional
    public CheckoutEntity beginCheckout(Long sessionId, String operator, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Checkout idempotency key is required");
        }
        CheckoutEntity existing = findCheckoutByIdempotencyKey(idempotencyKey);
        if (existing != null) {
            if (!sessionId.equals(existing.getSessionId())) {
                throw new IllegalStateException("Idempotency key belongs to another session");
            }
            return existing;
        }
        DiningSessionEntity session = requireDiningSession(sessionId);
        OrderEntity order = requireActiveOrder(sessionId);
        if (!"CONFIRMED".equals(order.getStatus())) {
            throw new IllegalStateException("Please confirm the order before checkout");
        }
        if (orderService.getActiveOrderItems(order.getId()).isEmpty()) {
            throw new IllegalStateException("Cannot checkout an empty order");
        }
        if (order.getFinalAmount() == null || order.getFinalAmount().signum() < 0) {
            throw new IllegalStateException("Order payable amount is invalid");
        }

        LocalDateTime now = LocalDateTime.now();
        CheckoutEntity checkout = new CheckoutEntity();
        checkout.setCheckoutNo(newCheckoutNo());
        checkout.setSessionId(sessionId);
        checkout.setOrderId(order.getId());
        checkout.setScope("FULL");
        checkout.setStatus("CREATED");
        checkout.setOriginalAmount(valueOrZero(order.getTotalAmount()));
        checkout.setDiscountAmount(valueOrZero(order.getDiscountAmount()));
        checkout.setPayableAmount(valueOrZero(order.getFinalAmount()));
        checkout.setPaidAmount(BigDecimal.ZERO);
        checkout.setChangeAmount(BigDecimal.ZERO);
        checkout.setIdempotencyKey(idempotencyKey.trim());
        checkout.setCreatedBy(normalizeOperator(operator));
        checkout.setCreatedAt(now);
        checkout.setUpdatedAt(now);
        MapperUtils.insert(checkoutMapper, checkout);

        DiningSessionEntity update = new DiningSessionEntity();
        update.setId(sessionId);
        update.setStatus("CHECKING");
        update.setCheckingAt(now);
        update.setUpdatedAt(now);
        MapperUtils.updateById(sessionMapper, update);
        log(sessionId, session.getPrimaryTableId(), "CHECKOUT_CREATED", null,
                checkout.getCheckoutNo(), null, operator);
        return checkout;
    }

    @Transactional
    public CheckoutResult markCheckoutPending(String checkoutNo, String paymentMethod) {
        CheckoutEntity checkout = getCheckout(checkoutNo);
        CheckoutEntity update = new CheckoutEntity();
        update.setCheckoutNo(checkoutNo);
        update.setStatus("PAYING");
        update.setPaymentMethod(paymentMethod);
        update.setUpdatedAt(LocalDateTime.now());
        MapperUtils.updateById(checkoutMapper, update);
        checkout.setStatus("PAYING");
        checkout.setPaymentMethod(paymentMethod);
        return toCheckoutResult(checkout);
    }

    @Transactional
    public void markCheckoutFailed(String checkoutNo, String operator, String reason) {
        CheckoutEntity checkout = getCheckout(checkoutNo);
        CheckoutEntity update = new CheckoutEntity();
        update.setCheckoutNo(checkoutNo);
        update.setStatus("FAILED");
        update.setUpdatedAt(LocalDateTime.now());
        MapperUtils.updateById(checkoutMapper, update);
        DiningSessionEntity sessionUpdate = new DiningSessionEntity();
        sessionUpdate.setId(checkout.getSessionId());
        sessionUpdate.setStatus("DINING");
        sessionUpdate.setUpdatedAt(LocalDateTime.now());
        MapperUtils.updateById(sessionMapper, sessionUpdate);
        log(checkout.getSessionId(), null, "CHECKOUT_FAILED", checkoutNo,
                "DINING", reason, operator);
    }

    @Transactional
    public CheckoutResult completeCheckout(String checkoutNo, String paymentMethod,
                                           BigDecimal receivedAmount, OrderPaymentResult paymentResult,
                                           String operator) {
        CheckoutEntity checkout = getCheckout(checkoutNo);
        if ("PAID".equals(checkout.getStatus())) {
            return toCheckoutResult(checkout);
        }
        if (!paymentResult.succeeded()) {
            throw new IllegalStateException("Payment has not succeeded");
        }
        DiningSessionEntity session = getSession(checkout.getSessionId());
        LocalDateTime now = LocalDateTime.now();
        orderService.settleOrder(checkout.getOrderId(), paymentMethod,
                checkout.getPayableAmount(), receivedAmount);

        CheckoutEntity checkoutUpdate = new CheckoutEntity();
        checkoutUpdate.setCheckoutNo(checkoutNo);
        checkoutUpdate.setStatus("PAID");
        checkoutUpdate.setPaidAmount(paymentResult.paidAmount());
        checkoutUpdate.setChangeAmount(valueOrZero(paymentResult.changeAmount()));
        checkoutUpdate.setPaymentMethod(paymentMethod);
        checkoutUpdate.setPaymentId(paymentResult.paymentId());
        checkoutUpdate.setPaidAt(now);
        checkoutUpdate.setUpdatedAt(now);
        MapperUtils.updateById(checkoutMapper, checkoutUpdate);

        DiningSessionEntity sessionUpdate = new DiningSessionEntity();
        sessionUpdate.setId(session.getId());
        sessionUpdate.setStatus("CLOSED");
        sessionUpdate.setPaidAt(now);
        sessionUpdate.setClosedAt(now);
        sessionUpdate.setUpdatedAt(now);
        MapperUtils.updateById(sessionMapper, sessionUpdate);

        Set<Long> releasedTables = new LinkedHashSet<>();
        for (TableOccupancyEntity occupancy : activeOccupanciesForSession(session.getId())) {
            releaseOccupancy(occupancy, "SETTLED");
            releasedTables.add(occupancy.getTableId());
        }
        releasedTables.forEach(this::refreshReleasedTableStatus);
        log(session.getId(), session.getPrimaryTableId(), "SETTLE", checkoutNo,
                paymentResult.paymentId(), null, operator);

        checkout.setStatus("PAID");
        checkout.setPaidAmount(paymentResult.paidAmount());
        checkout.setChangeAmount(valueOrZero(paymentResult.changeAmount()));
        checkout.setPaymentMethod(paymentMethod);
        checkout.setPaymentId(paymentResult.paymentId());
        checkout.setPaidAt(now);
        return toCheckoutResult(checkout);
    }

    public DiningSessionEntity getSession(Long sessionId) {
        return MapperUtils.getById(sessionMapper, sessionId, "Dining session not found: " + sessionId);
    }

    public CheckoutEntity getCheckout(String checkoutNo) {
        return MapperUtils.getById(checkoutMapper, checkoutNo, "Checkout not found: " + checkoutNo);
    }

    public CheckoutEntity findCheckoutByIdempotencyKey(String idempotencyKey) {
        List<CheckoutEntity> checkouts = MapperUtils.selectList(checkoutMapper, CheckoutEntity.class,
                wrapper -> wrapper.eq("idempotency_key", idempotencyKey.trim()).last("LIMIT 1"));
        return checkouts.isEmpty() ? null : checkouts.getFirst();
    }

    private SessionSummary sessionSummary(Long sessionId) {
        DiningSessionEntity session = MapperUtils.getById(sessionMapper, sessionId, null);
        if (session == null || List.of("CLOSED", "CANCELLED", "EXPIRED").contains(session.getStatus())) {
            return null;
        }
        OrderEntity order = orderService.findActiveOrderBySessionId(sessionId);
        return new SessionSummary(
                session.getId(),
                session.getSessionNo(),
                session.getGuestCount(),
                session.getStatus(),
                order == null ? null : order.getId(),
                order == null ? null : order.getOrderNo(),
                order == null ? BigDecimal.ZERO : valueOrZero(order.getFinalAmount()),
                session.getOpenedAt()
        );
    }

    private String deriveStatus(TableEntity table, List<SessionSummary> sessions) {
        if (table.getOperationalStatus() != null
                && List.of("DISABLED", "MAINTENANCE").contains(table.getOperationalStatus())) {
            return table.getOperationalStatus();
        }
        if ("CLEANING".equals(table.getStatus()) || "CLEANING".equals(table.getOperationalStatus())) {
            return "CLEANING";
        }
        if (sessions.stream().anyMatch(session -> "CHECKING".equals(session.status()))) {
            return "CHECKING";
        }
        if (sessions.size() > 1) {
            return "SHARED";
        }
        return sessions.isEmpty() ? "FREE" : "OCCUPIED";
    }

    private DiningSessionEntity requireDiningSession(Long sessionId) {
        DiningSessionEntity session = getSession(sessionId);
        if (!"DINING".equals(session.getStatus())) {
            throw new IllegalStateException("Dining session is not editable in status " + session.getStatus());
        }
        return session;
    }

    private OrderEntity requireActiveOrder(Long sessionId) {
        OrderEntity order = orderService.findActiveOrderBySessionId(sessionId);
        if (order == null) {
            throw new IllegalStateException("Active order was not found for dining session " + sessionId);
        }
        return order;
    }

    private List<TableOccupancyEntity> activeOccupanciesForTable(Long tableId) {
        return MapperUtils.selectList(occupancyMapper, TableOccupancyEntity.class,
                wrapper -> wrapper.eq("table_id", tableId)
                        .eq("status", "ACTIVE")
                        .orderByAsc("occupied_at"));
    }

    private List<TableOccupancyEntity> activeOccupanciesForSession(Long sessionId) {
        return MapperUtils.selectList(occupancyMapper, TableOccupancyEntity.class,
                wrapper -> wrapper.eq("session_id", sessionId)
                        .eq("status", "ACTIVE")
                        .orderByAsc("occupied_at"));
    }

    private void insertOccupancy(Long tableId, Long sessionId, String mode,
                                 Integer seatCount, Long fromOccupancyId) {
        LocalDateTime now = LocalDateTime.now();
        TableOccupancyEntity occupancy = new TableOccupancyEntity();
        occupancy.setTableId(tableId);
        occupancy.setSessionId(sessionId);
        occupancy.setMode(mode);
        occupancy.setSeatCount(seatCount);
        occupancy.setStatus("ACTIVE");
        occupancy.setOccupiedAt(now);
        occupancy.setFromOccupancyId(fromOccupancyId);
        occupancy.setCreatedAt(now);
        occupancy.setUpdatedAt(now);
        MapperUtils.insert(occupancyMapper, occupancy);
    }

    private void releaseOccupancy(TableOccupancyEntity occupancy, String reason) {
        TableOccupancyEntity update = new TableOccupancyEntity();
        update.setId(occupancy.getId());
        update.setStatus("RELEASED");
        update.setReleasedAt(LocalDateTime.now());
        update.setReleaseReason(reason);
        update.setUpdatedAt(LocalDateTime.now());
        MapperUtils.updateById(occupancyMapper, update);
    }

    private void updateOccupancySeats(TableOccupancyEntity occupancy, Integer guestCount) {
        TableOccupancyEntity update = new TableOccupancyEntity();
        update.setId(occupancy.getId());
        update.setSeatCount(guestCount);
        update.setUpdatedAt(LocalDateTime.now());
        MapperUtils.updateById(occupancyMapper, update);
    }

    private void refreshReleasedTableStatus(Long tableId) {
        if (activeOccupanciesForTable(tableId).isEmpty()) {
            tableService.updateRuntimeStatus(tableId, "CLEANING", "CLEANING");
        } else {
            tableService.updateRuntimeStatus(tableId, "OCCUPIED", "AVAILABLE");
        }
    }

    private void lockTables(Long firstTableId, Long secondTableId) {
        List.of(firstTableId, secondTableId).stream()
                .distinct()
                .sorted(Comparator.naturalOrder())
                .forEach(tableService::getTableForUpdate);
    }

    private void requireOperational(TableEntity table) {
        if (table.getOperationalStatus() != null
                && !"AVAILABLE".equals(table.getOperationalStatus())) {
            throw new IllegalStateException("Table is not operationally available");
        }
        if ("CLEANING".equals(table.getStatus())) {
            throw new IllegalStateException("Table must be cleaned before opening");
        }
    }

    private CheckoutResult toCheckoutResult(CheckoutEntity checkout) {
        return new CheckoutResult(
                checkout.getCheckoutNo(),
                checkout.getStatus(),
                checkout.getSessionId(),
                checkout.getOrderId(),
                checkout.getPayableAmount(),
                checkout.getPaidAmount(),
                checkout.getChangeAmount(),
                checkout.getPaymentId()
        );
    }

    private void log(Long sessionId, Long tableId, String operation,
                     String beforeValue, String afterValue, String reason, String operator) {
        TableOperationLogEntity entry = new TableOperationLogEntity();
        entry.setSessionId(sessionId);
        entry.setTableId(tableId);
        entry.setOperation(operation);
        entry.setBeforeValue(beforeValue);
        entry.setAfterValue(afterValue);
        entry.setReason(reason);
        entry.setOperator(normalizeOperator(operator));
        entry.setCreatedAt(LocalDateTime.now());
        MapperUtils.insert(operationLogMapper, entry);
    }

    private String newSessionNo() {
        return "S" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    private String newCheckoutNo() {
        return "C" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    private String normalizeOperator(String operator) {
        return operator == null || operator.isBlank() ? "system" : operator.trim();
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private BigDecimal valueOrZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
