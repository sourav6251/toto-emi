package com.emi.config;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.emi.config.properties.StorageProperties;
import io.imagekit.client.ImageKitClient;
import io.imagekit.client.okhttp.ImageKitOkHttpClient;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@AllArgsConstructor
@Slf4j
public class FileUploadConfig {

    private final StorageProperties storageProperties;

    @Bean
    public Cloudinary cloudinary() {
        String url = storageProperties.getCloudinary().getUrl();
        if (url != null && !url.isBlank()) {
            if (url.trim().startsWith("cloudinary://")) {
                return new Cloudinary(url.trim());
            }
            log.warn("Ignoring CLOUDINARY_URL: expected format cloudinary://<api_key>:<api_secret>@<cloud_name>. Falling back to CLOUD_NAME/CLOUD_API_KEY/CLOUD_API_SECRET.");
        }
        return new Cloudinary(
                ObjectUtils.asMap(
                        "cloud_name", storageProperties.getCloudinary().getCloudName(),
                        "api_key", storageProperties.getCloudinary().getApiKey(),
                        "api_secret", storageProperties.getCloudinary().getApiSecret(),
                        "secure", true));
    }

    @Bean
    public ImageKitClient imageKitClient() {

        return ImageKitOkHttpClient.builder()
                .privateKey(storageProperties.getImagekit().getPrivateKey())
//                .webhookSecret(webhookSecret)
                .build();
    }
}
