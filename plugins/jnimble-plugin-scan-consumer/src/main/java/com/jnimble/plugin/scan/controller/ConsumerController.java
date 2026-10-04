package com.jnimble.plugin.scan.controller;

import com.jnimble.plugin.scan.model.dto.ApiResult;
import com.jnimble.plugin.scan.model.entity.CartEntity;
import com.jnimble.plugin.scan.model.entity.CartItemEntity;
import com.jnimble.plugin.scan.model.entity.ConsumerEntity;
import com.jnimble.plugin.scan.model.entity.ScanTableEntity;
import com.jnimble.plugin.scan.model.entity.StoreEntity;
import com.jnimble.plugin.scan.service.CartService;
import com.jnimble.plugin.scan.service.ConsumerMenuService;
import com.jnimble.plugin.scan.service.ConsumerOrderService;
import com.jnimble.plugin.scan.service.ConsumerService;
import com.jnimble.plugin.scan.service.ScanTableService;
import com.jnimble.plugin.scan.service.StoreService;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/api")
public class ConsumerController {

    private final StoreService storeService;
    private final ScanTableService tableService;
    private final CartService cartService;
    private final ConsumerService consumerService;
    private final ConsumerOrderService consumerOrderService;
    private final ConsumerMenuService consumerMenuService;

    public ConsumerController(StoreService storeService,
                              ScanTableService tableService,
                              CartService cartService,
                              ConsumerService consumerService,
                              ConsumerOrderService consumerOrderService,
                              ConsumerMenuService consumerMenuService) {
        this.storeService = storeService;
        this.tableService = tableService;
        this.cartService = cartService;
        this.consumerService = consumerService;
        this.consumerOrderService = consumerOrderService;
        this.consumerMenuService = consumerMenuService;
    }

    // ==================== Store & Table ====================

    @GetMapping("/consumer/tables/{tableUUID}")
    @ResponseBody
    public Map<String, Object> getTableInfo(@PathVariable String tableUUID) {
        try {
            ScanTableEntity table = tableService.getTableByUuid(tableUUID);
            StoreEntity store = storeService.getStore(table.getStoreId());

            Map<String, Object> data = buildStoreTableResponse(store, table);
            return ApiResult.success(data);
        } catch (Exception e) {
            return ApiResult.error(e.getMessage());
        }
    }

    @GetMapping("/consumer/stores/{storeID}")
    @ResponseBody
    public Map<String, Object> getStoreInfo(@PathVariable Long storeID) {
        try {
            StoreEntity store = storeService.getStore(storeID);
            Map<String, Object> data = buildStoreResponse(store);
            return ApiResult.success(data);
        } catch (Exception e) {
            return ApiResult.error(e.getMessage());
        }
    }

    @PutMapping("/consumer/stores/{storeID}/latlng")
    @ResponseBody
    public Map<String, Object> updateStoreLatLng(@PathVariable Long storeID,
                                                  @RequestParam BigDecimal lat,
                                                  @RequestParam BigDecimal lng) {
        try {
            storeService.updateLatLng(storeID, lat, lng);
            return ApiResult.success("更新成功", new HashMap<>());
        } catch (Exception e) {
            return ApiResult.error(e.getMessage());
        }
    }

    // ==================== Goods / Menu ====================

    @GetMapping("/consumer/goods/groups")
    @ResponseBody
    public Map<String, Object> getGoodsGroups(@RequestParam(required = false, defaultValue = "") String keyword) {
        try {
            Map<String, Object> data = consumerMenuService.getGoodsGroups(keyword);
            return ApiResult.success(data);
        } catch (Exception e) {
            return ApiResult.error(e.getMessage());
        }
    }

    // ==================== Cart ====================

    @GetMapping("/consumer/cart")
    @ResponseBody
    public Map<String, Object> getCart(HttpServletRequest request,
                                        @RequestParam(required = false) Long id) {
        try {
            Long storeId = parseLong(request.getHeader("Store-ID"));
            String tableUUID = request.getHeader("Table-UUID");
            String accessToken = request.getHeader("Authorization");
            Long tableId = resolveTableId(storeId, tableUUID);

            CartEntity cart;
            if (id != null) {
                cart = cartService.getOrCreateCart(storeId, null, accessToken);
            } else {
                cart = cartService.getOrCreateCart(storeId, tableId, accessToken);
            }
            List<CartItemEntity> items = cartService.getCartItems(cart.getId());

            List<Map<String, Object>> carts = new ArrayList<>();
            for (CartItemEntity item : items) {
                Map<String, Object> cartVO = new HashMap<>();
                Map<String, Object> goods = new HashMap<>();
                goods.put("id", String.valueOf(item.getMenuItemId()));
                goods.put("name", item.getItemName());
                goods.put("pics", List.of());
                goods.put("formatSellPrice", item.getUnitPrice().toPlainString());
                goods.put("unit", "");
                cartVO.put("goods", goods);
                cartVO.put("id", String.valueOf(item.getId()));
                cartVO.put("count", item.getQuantity());
                cartVO.put("totalPrice", item.getTotalPrice());
                cartVO.put("formatPrice", item.getTotalPrice().toPlainString());
                cartVO.put("goodsTagsItems", List.of());
                cartVO.put("tags", item.getTagNames() == null ? "" : item.getTagNames());
                carts.add(cartVO);
            }

            Map<String, Object> data = new HashMap<>();
            data.put("carts", carts);
            data.put("formatTotalPrice", cartService.formatTotalPrice(items));
            data.put("formatTotalPrice2", cartService.formatTotalPrice2(items));
            return ApiResult.success(data);
        } catch (Exception e) {
            return ApiResult.success(buildEmptyCart());
        }
    }

    @PutMapping("/consumer/cart/goods/{goodsId}/count/{count}")
    @ResponseBody
    public Map<String, Object> updateCartGoods(HttpServletRequest request,
                                                @PathVariable Long goodsId,
                                                @PathVariable Integer count,
                                                @RequestParam(required = false, defaultValue = "") String tag_ids,
                                                @RequestParam(required = false, defaultValue = "1") Integer order_type) {
        try {
            Long storeId = parseLong(request.getHeader("Store-ID"));
            String tableUUID = request.getHeader("Table-UUID");
            String accessToken = request.getHeader("Authorization");
            Long tableId = resolveTableId(storeId, tableUUID);

            cartService.updateCartItemQuantity(storeId, tableId, accessToken, goodsId, count, tag_ids, order_type);

            CartEntity cart = cartService.getOrCreateCart(storeId, tableId, accessToken);
            List<CartItemEntity> items = cartService.getCartItems(cart.getId());

            List<Map<String, Object>> carts = new ArrayList<>();
            for (CartItemEntity item : items) {
                Map<String, Object> cartVO = new HashMap<>();
                cartVO.put("id", String.valueOf(item.getId()));
                cartVO.put("count", item.getQuantity());
                cartVO.put("totalPrice", item.getTotalPrice());
                carts.add(cartVO);
            }

            Map<String, Object> data = new HashMap<>();
            data.put("formatTotalPrice", cartService.formatTotalPrice(items));
            data.put("carts", carts);
            return ApiResult.success(data);
        } catch (Exception e) {
            return ApiResult.error(e.getMessage());
        }
    }

    // ==================== Orders ====================

    @GetMapping("/consumer/orders/unpaid")
    @ResponseBody
    public Map<String, Object> getUnpaidOrder(HttpServletRequest request) {
        try {
            Long storeId = parseLong(request.getHeader("Store-ID"));
            String tableUUID = request.getHeader("Table-UUID");
            String accessToken = request.getHeader("Authorization");
            Long tableId = resolveTableId(storeId, tableUUID);

            Map<String, Object> data = consumerOrderService.getUnpaidOrder(storeId, tableId, accessToken);
            return ApiResult.success(data);
        } catch (Exception e) {
            return ApiResult.error(1, e.getMessage());
        }
    }

    @PostMapping("/consumer/orders/type/{orderType}")
    @ResponseBody
    public Map<String, Object> createOrder(HttpServletRequest request,
                                            @PathVariable Integer orderType,
                                            @RequestParam(required = false, defaultValue = "1") Integer people_count,
                                            @RequestParam(required = false) String order_start_time,
                                            @RequestParam(required = false, defaultValue = "") String note) {
        try {
            Long storeId = parseLong(request.getHeader("Store-ID"));
            String tableUUID = request.getHeader("Table-UUID");
            String accessToken = request.getHeader("Authorization");
            Long tableId = resolveTableId(storeId, tableUUID);

            Map<String, Object> data = consumerOrderService.createOrder(
                    storeId, tableId, accessToken, orderType, people_count, order_start_time, note
            );
            return ApiResult.success(data);
        } catch (Exception e) {
            return ApiResult.error(e.getMessage());
        }
    }

    @GetMapping("/consumer/orders/{orderId}")
    @ResponseBody
    public Map<String, Object> getOrderDetail(@PathVariable Long orderId, HttpServletRequest request) {
        try {
            String accessToken = request.getHeader("Authorization");
            Map<String, Object> data = consumerOrderService.getOrderDetail(orderId, accessToken);
            return ApiResult.success(data);
        } catch (Exception e) {
            return ApiResult.error(e.getMessage());
        }
    }

    @GetMapping("/consumer/orders")
    @ResponseBody
    public Map<String, Object> listOrders(HttpServletRequest request,
                                           @RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "10") int pageSize) {
        try {
            Long storeId = parseLong(request.getHeader("Store-ID"));
            String tableUUID = request.getHeader("Table-UUID");
            String accessToken = request.getHeader("Authorization");
            Long tableId = resolveTableId(storeId, tableUUID);
            Map<String, Object> data = consumerOrderService.listOrders(accessToken, storeId, tableId, page, pageSize);
            return ApiResult.success(data);
        } catch (Exception e) {
            return ApiResult.error(e.getMessage());
        }
    }

    // ==================== Consumer Info ====================

    @GetMapping("/consumer/info")
    @ResponseBody
    public Map<String, Object> getConsumerInfo(HttpServletRequest request) {
        try {
            String accessToken = request.getHeader("Authorization");
            ConsumerEntity consumer = consumerService.getConsumerByAccessToken(accessToken);
            if (consumer == null) {
                consumer = consumerService.getOrCreateDemoConsumer();
            }

            Map<String, Object> consumerInfo = new HashMap<>();
            consumerInfo.put("photoUrl", consumer.getPhotoUrl() == null ? "" : consumer.getPhotoUrl());
            consumerInfo.put("nickName", consumer.getNickName() == null ? "" : consumer.getNickName());
            consumerInfo.put("uuid", consumer.getUuid());

            Map<String, Object> data = new HashMap<>();
            data.put("consumer", consumerInfo);
            return ApiResult.success(data);
        } catch (Exception e) {
            return ApiResult.error(e.getMessage());
        }
    }

    // ==================== Plugin Hook ====================

    @GetMapping("/consumer/plugins/hook")
    @ResponseBody
    public Map<String, Object> getPluginHook(@RequestParam String hookKey,
                                              @RequestParam(required = false) String redirect_url) {
        try {
            List<Map<String, Object>> itemsVo = new ArrayList<>();

            if ("consumer.app.social.oauths".equals(hookKey)) {
                Map<String, Object> item = new HashMap<>();
                item.put("label", "短信验证码登录");
                item.put("path", "/uni_modules/scan-food-consumer-oauth-sms-code/pages/index");
                item.put("icon", "");
                item.put("badge", 0);
                item.put("badge_type", "");
                item.put("value", "");
                item.put("des", "");
                itemsVo.add(item);
            }

            Map<String, Object> data = new HashMap<>();
            data.put("itemsVo", itemsVo);
            return ApiResult.success(data);
        } catch (Exception e) {
            return ApiResult.error(e.getMessage());
        }
    }

    // ==================== OAuth Callback ====================

    @GetMapping("/consumer/app_oauth_callback/{pluginId}")
    @ResponseBody
    public Map<String, Object> oauthCallback(@PathVariable String pluginId,
                                              HttpServletRequest request) {
        try {
            ConsumerEntity consumer = consumerService.getOrCreateDemoConsumer();

            Map<String, Object> consumerData = new HashMap<>();
            Map<String, Object> user = new HashMap<>();
            user.put("accessToken", consumer.getAccessToken());
            user.put("id", String.valueOf(consumer.getId()));
            consumerData.put("user", user);

            Map<String, Object> oauthUser = new HashMap<>();
            oauthUser.put("openid", consumer.getUuid());

            Map<String, Object> data = new HashMap<>();
            data.put("consumer", consumerData);
            data.put("oauthUser", oauthUser);
            return ApiResult.success(data);
        } catch (Exception e) {
            return ApiResult.error(e.getMessage());
        }
    }

    // ==================== Common ====================

    @GetMapping("/common/currency_rate")
    @ResponseBody
    public Map<String, Object> getCurrencyRate(@RequestParam(required = false, defaultValue = "CNY") String fromCode,
                                                @RequestParam(required = false, defaultValue = "CNY") String toCode) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("currency_rate", BigDecimal.ONE);
            return ApiResult.success(data);
        } catch (Exception e) {
            return ApiResult.error(e.getMessage());
        }
    }

    // ==================== Zone ====================

    @GetMapping("/zone/countries")
    @ResponseBody
    public Map<String, Object> getCountries() {
        try {
            List<Map<String, Object>> countries = new ArrayList<>();
            Map<String, Object> cn = new HashMap<>();
            cn.put("countryAbbr", "CN");
            cn.put("countryName", "中国");
            countries.add(cn);

            Map<String, Object> data = new HashMap<>();
            data.put("countries", countries);
            return ApiResult.success(data);
        } catch (Exception e) {
            return ApiResult.error(e.getMessage());
        }
    }

    @GetMapping("/zone/country/{countryAbbr}/zones")
    @ResponseBody
    public Map<String, Object> getZones(@PathVariable String countryAbbr) {
        try {
            List<Map<String, Object>> zones = new ArrayList<>();
            Map<String, Object> zone = new HashMap<>();
            zone.put("zoneAbbr", "GD");
            zone.put("zoneName", "广东");
            zones.add(zone);

            Map<String, Object> data = new HashMap<>();
            data.put("zones", zones);
            return ApiResult.success(data);
        } catch (Exception e) {
            return ApiResult.error(e.getMessage());
        }
    }

    // ==================== Helper Methods ====================

    private Map<String, Object> buildStoreTableResponse(StoreEntity store, ScanTableEntity table) {
        Map<String, Object> data = new HashMap<>();
        data.put("store", buildStoreInfo(store));
        data.put("table", buildTableInfo(table));
        data.put("currencies", buildCurrencies(store));
        data.put("languages", buildLanguages());
        data.put("is_login", false);
        data.put("location_distance", "0");
        data.put("location_distance_config", 0);
        data.put("plan_order_config", 0);
        data.put("queue_config", 0);
        data.put("book_table_confing", 0);
        return data;
    }

    private Map<String, Object> buildStoreResponse(StoreEntity store) {
        Map<String, Object> data = new HashMap<>();
        data.put("store", buildStoreInfo(store));
        data.put("currencies", buildCurrencies(store));
        data.put("languages", buildLanguages());
        data.put("is_login", false);
        data.put("book_table_confing", 0);
        data.put("location_distance_config", 0);
        data.put("plan_order_config", 0);
        data.put("queue_config", 0);
        return data;
    }

    private Map<String, Object> buildStoreInfo(StoreEntity store) {
        Map<String, Object> info = new HashMap<>();
        info.put("id", String.valueOf(store.getId()));
        info.put("name", store.getName());
        info.put("lat", store.getLat() == null ? BigDecimal.ZERO : store.getLat());
        info.put("lng", store.getLng() == null ? BigDecimal.ZERO : store.getLng());
        info.put("address", store.getAddress() == null ? "" : store.getAddress());
        info.put("phone", store.getPhone() == null ? "" : store.getPhone());
        info.put("defaultCurrencyCode", store.getDefaultCurrencyCode() == null ? "CNY" : store.getDefaultCurrencyCode());
        info.put("currencySymbol", store.getCurrencySymbol() == null ? "¥" : store.getCurrencySymbol());
        return info;
    }

    private Map<String, Object> buildTableInfo(ScanTableEntity table) {
        Map<String, Object> info = new HashMap<>();
        info.put("id", String.valueOf(table.getId()));
        info.put("uuid", table.getUuid());
        info.put("tableName", table.getTableName());
        info.put("code", table.getCode());
        return info;
    }

    private List<Map<String, Object>> buildCurrencies(StoreEntity store) {
        List<Map<String, Object>> currencies = new ArrayList<>();
        Map<String, Object> currency = new HashMap<>();
        currency.put("code", store.getDefaultCurrencyCode() == null ? "CNY" : store.getDefaultCurrencyCode());
        String symbol = store.getCurrencySymbol() == null ? "¥" : store.getCurrencySymbol();
        currency.put("symbol", symbol);
        currency.put("symol", symbol);
        currency.put("name", "人民币");
        currencies.add(currency);
        return currencies;
    }

    private List<Map<String, Object>> buildLanguages() {
        List<Map<String, Object>> languages = new ArrayList<>();
        Map<String, Object> zh = new HashMap<>();
        zh.put("code", "zh_CN");
        zh.put("locale", "zh_CN");
        zh.put("name", "简体中文");
        languages.add(zh);

        Map<String, Object> en = new HashMap<>();
        en.put("code", "en_US");
        en.put("locale", "en_US");
        en.put("name", "English");
        languages.add(en);
        return languages;
    }

    private Map<String, Object> buildEmptyCart() {
        Map<String, Object> data = new HashMap<>();
        data.put("carts", List.of());
        data.put("formatTotalPrice", "0.00");
        data.put("formatTotalPrice2", "0.00");
        return data;
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank() || "null".equals(value)) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long resolveTableId(Long storeId, String tableUUID) {
        if (tableUUID == null || tableUUID.isBlank() || "null".equals(tableUUID)) {
            return null;
        }
        try {
            ScanTableEntity table = tableService.getTableByUuid(tableUUID);
            return table.getId();
        } catch (Exception e) {
            return null;
        }
    }
}
