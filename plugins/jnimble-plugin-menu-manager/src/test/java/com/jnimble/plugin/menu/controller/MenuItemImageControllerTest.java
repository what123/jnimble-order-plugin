package com.jnimble.plugin.menu.controller;

import com.jnimble.platform.auth.ControllerAuthorization;
import com.jnimble.plugin.menu.service.MenuItemImageStorageService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MenuItemImageControllerTest {

    @Mock
    private MenuItemImageStorageService storageService;

    @Mock
    private ControllerAuthorization authorization;

    @InjectMocks
    private MenuItemImageController controller;

    @Test
    void uploadReturnsStoredImagePath() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "dish.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[]{1}
        );
        doNothing().when(authorization).requirePermission("menu-manager.item.manage");
        when(storageService.store(file)).thenReturn(
                new MenuItemImageStorageService.StoredImage("00000000-0000-0000-0000-000000000001.jpg")
        );

        ResponseEntity<Map<String, Object>> response = controller.upload(file);

        assertEquals(200, response.getStatusCode().value());
        assertTrue((Boolean) response.getBody().get("success"));
        assertEquals(
                "/admin/plugins/menu-manager/items/images/00000000-0000-0000-0000-000000000001.jpg",
                response.getBody().get("imagePath")
        );
        verify(authorization).requirePermission("menu-manager.item.manage");
    }

    @Test
    void uploadReturnsBadRequestForInvalidImage() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "dish.svg", "image/svg+xml", new byte[]{1}
        );
        when(storageService.store(file)).thenThrow(new IllegalArgumentException("不支持的图片"));

        ResponseEntity<Map<String, Object>> response = controller.upload(file);

        assertEquals(400, response.getStatusCode().value());
        assertFalse((Boolean) response.getBody().get("success"));
        assertEquals("不支持的图片", response.getBody().get("message"));
    }
}
