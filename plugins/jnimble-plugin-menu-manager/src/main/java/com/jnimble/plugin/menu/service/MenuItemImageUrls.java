package com.jnimble.plugin.menu.service;

public final class MenuItemImageUrls {

    public static final String ADMIN_BASE_PATH = "/admin/plugins/menu-manager/items/images/";
    public static final String PUBLIC_BASE_PATH = "/api/menu/images/";

    private MenuItemImageUrls() {
    }

    public static String toPublic(String imagePath) {
        if (imagePath == null || imagePath.isBlank()) {
            return imagePath;
        }
        if (imagePath.startsWith(ADMIN_BASE_PATH)) {
            return PUBLIC_BASE_PATH + imagePath.substring(ADMIN_BASE_PATH.length());
        }
        return imagePath;
    }
}
