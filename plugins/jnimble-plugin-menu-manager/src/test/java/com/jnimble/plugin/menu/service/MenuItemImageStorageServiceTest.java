package com.jnimble.plugin.menu.service;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MenuItemImageStorageServiceTest {

    @TempDir
    private Path tempDirectory;

    private MenuItemImageStorageService storageService;

    @BeforeEach
    void setUp() {
        storageService = new MenuItemImageStorageService(tempDirectory.toString());
    }

    @Test
    void storesAndLoadsSupportedImage() throws Exception {
        MockMultipartFile image = new MockMultipartFile(
                "file",
                "dish.png",
                MediaType.IMAGE_PNG_VALUE,
                new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a}
        );

        MenuItemImageStorageService.StoredImage stored = storageService.store(image);

        assertTrue(stored.fileName().endsWith(".png"));
        assertTrue(Files.exists(tempDirectory.resolve(stored.fileName())));
        assertTrue(storageService.load(stored.fileName()).isReadable());
        assertEquals(MediaType.IMAGE_PNG, storageService.mediaType(stored.fileName()));
    }

    @Test
    void rejectsUnsupportedFileType() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "dish.svg", "image/svg+xml", "<svg/>".getBytes()
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> storageService.store(file)
        );

        assertTrue(exception.getMessage().contains("JPG"));
    }

    @Test
    void rejectsImageWhoseContentDoesNotMatchItsType() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "dish.png", MediaType.IMAGE_PNG_VALUE, new byte[]{1, 2, 3}
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> storageService.store(file)
        );

        assertTrue(exception.getMessage().contains("内容无效"));
    }

    @Test
    void rejectsImageLargerThanFiveMegabytes() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "dish.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                new byte[(int) MenuItemImageStorageService.MAX_IMAGE_BYTES + 1]
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> storageService.store(file)
        );

        assertTrue(exception.getMessage().contains("5MB"));
    }

    @Test
    void rejectsUnsafeStoredFileName() {
        assertThrows(IllegalArgumentException.class, () -> storageService.load("../dish.png"));
    }
}
