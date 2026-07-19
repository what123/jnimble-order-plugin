package com.jnimble.plugin.menu.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MenuItemImageStorageService {

    static final long MAX_IMAGE_BYTES = 5L * 1024L * 1024L;

    private static final Map<String, String> EXTENSIONS = Map.of(
            MediaType.IMAGE_JPEG_VALUE, "jpg",
            MediaType.IMAGE_PNG_VALUE, "png",
            MediaType.IMAGE_GIF_VALUE, "gif",
            "image/webp", "webp"
    );

    private final Path storageDirectory;

    public MenuItemImageStorageService(
            @Value("${jnimble.menu-manager.image-storage-dir:./data/menu-item-images}") String storageDirectory
    ) {
        this.storageDirectory = Path.of(storageDirectory).toAbsolutePath().normalize();
    }

    public StoredImage store(MultipartFile file) {
        requireValidImage(file);
        String contentType = normalizeContentType(file.getContentType());
        String fileName = UUID.randomUUID() + "." + EXTENSIONS.get(contentType);
        Path target = resolve(fileName);
        try {
            Files.createDirectories(storageDirectory);
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("图片保存失败", ex);
        }
        return new StoredImage(fileName);
    }

    public Resource load(String fileName) {
        Path imagePath = resolve(fileName);
        try {
            Resource resource = new UrlResource(imagePath.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new IllegalArgumentException("图片不存在");
            }
            return resource;
        } catch (IOException ex) {
            throw new IllegalArgumentException("图片不存在", ex);
        }
    }

    public MediaType mediaType(String fileName) {
        String extension = extension(fileName);
        return switch (extension) {
            case "jpg" -> MediaType.IMAGE_JPEG;
            case "png" -> MediaType.IMAGE_PNG;
            case "gif" -> MediaType.IMAGE_GIF;
            case "webp" -> MediaType.parseMediaType("image/webp");
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }

    private void requireValidImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要上传的图片");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new IllegalArgumentException("单张图片不能超过5MB");
        }
        String contentType = normalizeContentType(file.getContentType());
        if (!EXTENSIONS.containsKey(contentType)) {
            throw new IllegalArgumentException("仅支持 JPG、PNG、GIF 或 WebP 图片");
        }
        if (!hasExpectedSignature(file, contentType)) {
            throw new IllegalArgumentException("图片文件内容无效");
        }
    }

    private boolean hasExpectedSignature(MultipartFile file, String contentType) {
        try (InputStream inputStream = file.getInputStream()) {
            byte[] header = inputStream.readNBytes(12);
            return switch (contentType) {
                case MediaType.IMAGE_JPEG_VALUE -> startsWith(header, 0xff, 0xd8, 0xff);
                case MediaType.IMAGE_PNG_VALUE -> startsWith(
                        header, 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a);
                case MediaType.IMAGE_GIF_VALUE -> startsWith(header, 'G', 'I', 'F', '8')
                        && header.length >= 6
                        && (header[4] == '7' || header[4] == '9')
                        && header[5] == 'a';
                case "image/webp" -> startsWith(header, 'R', 'I', 'F', 'F')
                        && header.length >= 12
                        && header[8] == 'W'
                        && header[9] == 'E'
                        && header[10] == 'B'
                        && header[11] == 'P';
                default -> false;
            };
        } catch (IOException ex) {
            throw new IllegalArgumentException("无法读取图片文件", ex);
        }
    }

    private boolean startsWith(byte[] bytes, int... prefix) {
        if (bytes.length < prefix.length) return false;
        for (int index = 0; index < prefix.length; index++) {
            if ((bytes[index] & 0xff) != (prefix[index] & 0xff)) return false;
        }
        return true;
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null) return "";
        int parameterIndex = contentType.indexOf(';');
        String normalized = parameterIndex >= 0 ? contentType.substring(0, parameterIndex) : contentType;
        return normalized.trim().toLowerCase(Locale.ROOT);
    }

    private Path resolve(String fileName) {
        if (fileName == null || !fileName.matches("[0-9a-fA-F-]{36}\\.(jpg|png|gif|webp)")) {
            throw new IllegalArgumentException("图片路径无效");
        }
        Path resolved = storageDirectory.resolve(fileName).normalize();
        if (!resolved.startsWith(storageDirectory)) {
            throw new IllegalArgumentException("图片路径无效");
        }
        return resolved;
    }

    private String extension(String fileName) {
        int separator = fileName == null ? -1 : fileName.lastIndexOf('.');
        return separator < 0 ? "" : fileName.substring(separator + 1).toLowerCase(Locale.ROOT);
    }

    public record StoredImage(String fileName) {
    }
}
