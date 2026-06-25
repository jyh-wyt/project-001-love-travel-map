package com.lovetravel.server.modules.oss.service;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ObjectMetadata;
import com.lovetravel.server.common.ApiException;
import com.lovetravel.server.modules.auth.mapper.AppUserMapper;
import com.lovetravel.server.modules.auth.domain.AppUser;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.lovetravel.server.modules.oss.config.OssProperties;
import com.lovetravel.server.modules.oss.vo.OssUploadResponse;

@Service
public class OssService {

    private static final long MAX_IMAGE_SIZE = 10 * 1024 * 1024;
    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM");

    private final OssProperties properties;
    private final AppUserMapper appUserMapper;

    public OssService(OssProperties properties, AppUserMapper appUserMapper) {
        this.properties = properties;
        this.appUserMapper = appUserMapper;
    }

    public OssUploadResponse uploadImage(Long userId, MultipartFile file) {
        requireUser(userId);
        validateConfig();
        validateImage(file);

        String objectKey = buildObjectKey(userId, file.getOriginalFilename());
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType(file.getContentType());
        metadata.setContentLength(file.getSize());

        OSS ossClient = new OSSClientBuilder().build(
                properties.getEndpoint(),
                properties.getAccessKeyId(),
                properties.getAccessKeySecret());
        try {
            ossClient.putObject(properties.getBucket(), objectKey, file.getInputStream(), metadata);
        } catch (IOException exception) {
            throw new ApiException("读取图片文件失败，请重新选择图片");
        } catch (RuntimeException exception) {
            throw new ApiException("上传 OSS 失败，请检查 OSS 配置或网络");
        } finally {
            ossClient.shutdown();
        }

        return new OssUploadResponse(resolveDisplayUrl(objectKey), objectKey);
    }

    public void deleteObjectIfPresent(String objectKey) {
        if (isBlank(objectKey)) {
            return;
        }
        validateConfig();

        OSS ossClient = new OSSClientBuilder().build(
                properties.getEndpoint(),
                properties.getAccessKeyId(),
                properties.getAccessKeySecret());
        try {
            ossClient.deleteObject(properties.getBucket(), objectKey);
        } catch (RuntimeException exception) {
            throw new ApiException("删除 OSS 图片失败，请稍后重试");
        } finally {
            ossClient.shutdown();
        }
    }

    public String resolveDisplayUrl(String objectKey) {
        if (isBlank(objectKey)) {
            return "";
        }
        if (!isBlank(properties.getPublicBaseUrl())) {
            return trimRightSlash(properties.getPublicBaseUrl()) + "/" + objectKey;
        }
        validateConfig();

        OSS ossClient = new OSSClientBuilder().build(
                properties.getEndpoint(),
                properties.getAccessKeyId(),
                properties.getAccessKeySecret());
        try {
            Date expiration = new Date(System.currentTimeMillis() + signedUrlExpireMillis());
            return ossClient.generatePresignedUrl(properties.getBucket(), objectKey, expiration).toString();
        } catch (RuntimeException exception) {
            throw new ApiException("生成 OSS 图片访问地址失败，请稍后重试");
        } finally {
            ossClient.shutdown();
        }
    }

    public Map<String, Object> getStatus() {
        boolean configured = !isBlank(properties.getEndpoint())
                && !isBlank(properties.getBucket())
                && !isBlank(properties.getAccessKeyId())
                && !isBlank(properties.getAccessKeySecret());
        return Map.of(
                "configured", configured,
                "endpoint", properties.getEndpoint() == null ? "" : properties.getEndpoint(),
                "bucket", properties.getBucket() == null ? "" : properties.getBucket(),
                "signedUrlExpireMinutes", properties.getSignedUrlExpireMinutes() == null ? 10 : properties.getSignedUrlExpireMinutes());
    }

    private void requireUser(Long userId) {
        AppUser user = appUserMapper.selectById(userId);
        if (user == null || Integer.valueOf(1).equals(user.getDeleted())) {
            throw new ApiException("用户不存在");
        }
    }

    private void validateConfig() {
        if (isBlank(properties.getEndpoint())
                || isBlank(properties.getBucket())
                || isBlank(properties.getAccessKeyId())
                || isBlank(properties.getAccessKeySecret())) {
            throw new ApiException("OSS 配置不完整，请先配置 endpoint、bucket 和 AccessKey");
        }
    }

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException("请选择要上传的图片");
        }
        if (file.getSize() > MAX_IMAGE_SIZE) {
            throw new ApiException("单张图片不能超过 10MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new ApiException("仅支持 JPG、PNG、WebP 或 GIF 图片");
        }
    }

    private String buildObjectKey(Long userId, String originalFilename) {
        String extension = resolveExtension(originalFilename);
        String month = LocalDate.now().format(MONTH_FORMATTER);
        return "travel/" + userId + "/" + month + "/" + UUID.randomUUID() + extension;
    }

    private String resolveExtension(String originalFilename) {
        if (originalFilename == null || !originalFilename.contains(".")) {
            return ".jpg";
        }
        String extension = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase(Locale.ROOT);
        return switch (extension) {
            case ".jpg", ".jpeg", ".png", ".webp", ".gif" -> extension;
            default -> ".jpg";
        };
    }

    private long signedUrlExpireMillis() {
        int minutes = properties.getSignedUrlExpireMinutes() == null ? 10 : properties.getSignedUrlExpireMinutes();
        return Math.max(1, minutes) * 60L * 1000L;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String trimRightSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
