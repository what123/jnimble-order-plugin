package com.jnimble.plugin.menu.controller;

import com.jnimble.plugin.menu.service.MenuItemImageStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicMenuItemImageControllerTest {

    @Mock
    private MenuItemImageStorageService storageService;

    @InjectMocks
    private PublicMenuItemImageController controller;

    @Test
    void imageReturnsPublicResource() {
        String fileName = "00000000-0000-0000-0000-000000000001.jpg";
        Resource resource = new ByteArrayResource(new byte[]{1, 2, 3});
        when(storageService.load(fileName)).thenReturn(resource);
        when(storageService.mediaType(fileName)).thenReturn(MediaType.IMAGE_JPEG);

        ResponseEntity<Resource> response = controller.image(fileName);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(MediaType.IMAGE_JPEG, response.getHeaders().getContentType());
        assertEquals(resource, response.getBody());
    }

    @Test
    void imageReturnsNotFoundForInvalidName() {
        when(storageService.load("../secret")).thenThrow(new IllegalArgumentException("图片路径无效"));

        assertThrows(ResponseStatusException.class, () -> controller.image("../secret"));
    }
}
