package com.jnimble.plugin.scan.model.dto;

import java.math.BigDecimal;
import java.util.List;

public final class ConsumerModels {

    private ConsumerModels() {}

    public record TableInitResponse(
            StoreInfo store,
            TableInfo table,
            List<CurrencyInfo> currencies,
            List<LanguageInfo> languages,
            boolean isLogin,
            String locationDistance,
            Object locationDistanceConfig,
            Object planOrderConfig,
            Object queueConfig,
            Object bookTableConfig
    ) {}

    public record StoreInitResponse(
            StoreInfo store,
            List<CurrencyInfo> currencies,
            List<LanguageInfo> languages,
            boolean isLogin,
            Object bookTableConfig,
            Object locationDistanceConfig,
            Object planOrderConfig,
            Object queueConfig
    ) {}

    public record StoreInfo(
            String id,
            String name,
            BigDecimal lat,
            BigDecimal lng,
            String address,
            String phone,
            String defaultCurrencyCode,
            String currencySymbol
    ) {}

    public record TableInfo(
            String id,
            String uuid,
            String tableName,
            String code
    ) {}

    public record CurrencyInfo(
            String code,
            String symbol,
            String name
    ) {}

    public record LanguageInfo(
            String code,
            String name
    ) {}

    public record GoodsGroup(
            String id,
            String name,
            List<GoodsItem> goods
    ) {}

    public record GoodsItem(
            String id,
            String name,
            List<PicInfo> pics,
            BigDecimal sellPrice,
            BigDecimal markerPrice,
            String formatSellPrice,
            String formatMarkerPrice,
            String displayPrice,
            String unit,
            List<String> goodsTags,
            List<GoodsTagItem> goodsTagsItems
    ) {}

    public record PicInfo(
            String showUrl,
            String showThumbnail
    ) {}

    public record GoodsTagItem(
            String id,
            String name
    ) {}

    public record CartResponse(
            List<CartVO> carts,
            String formatTotalPrice,
            String formatTotalPrice2
    ) {}

    public record CartVO(
            String id,
            GoodsItem goods,
            int count,
            BigDecimal totalPrice,
            String formatPrice,
            List<GoodsTagItem> goodsTagsItems,
            String tags
    ) {}

    public record UnpaidOrderResponse(
            OrderSummary order,
            List<OrderGoodsIndexGroup> orderGoodsIndexGroups
    ) {}

    public record OrderSummary(
            String id,
            BigDecimal totalPrice
    ) {}

    public record OrderGoodsIndexGroup(
            int indexGroup,
            List<OrderGoodsItem> orderGoodsList
    ) {}

    public record OrderGoodsItem(
            GoodsItem goods,
            int count,
            String goodsTagItemNames,
            String goodsName
    ) {}

    public record CreateOrderResponse(
            CreatedOrder order
    ) {}

    public record CreatedOrder(
            String id
    ) {}

    public record OrderDetailResponse(
            OrderDetail order,
            List<OrderGoodsItem> orderGoods,
            List<OrderGoodsIndexGroup> orderGoodsIndexGroups,
            Object itemsVo,
            String orderNoQrCode
    ) {}

    public record OrderDetail(
            String id,
            int payStatus,
            int type,
            String getOrderNumber,
            String orderNo,
            String formatTotalPrice,
            String formatDiscountPrice,
            String formatFinalPrice,
            String statusStr,
            String createTimeStr,
            String startTimeStr,
            String note,
            BigDecimal discountPrice
    ) {}

    public record OrderListResponse(
            OrderPage orders
    ) {}

    public record OrderPage(
            List<OrderListItem> content,
            int totalPages
    ) {}

    public record OrderListItem(
            String id,
            String orderNo,
            String statusStr,
            String formatFinalPrice,
            List<OrderGoodsItem> orderGoodsList
    ) {}

    public record ConsumerInfo(
            String photoUrl,
            String nickName,
            String uuid
    ) {}

    public record PluginHookResponse(
            List<HookItem> itemsVo
    ) {}

    public record HookItem(
            String label,
            String path,
            String icon,
            int badge,
            String badgeType,
            String value,
            String des
    ) {}
}
