package com.emi.config;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.emi.config.properties.StorageProperties;
import io.imagekit.client.ImageKitClient;
import io.imagekit.client.okhttp.ImageKitOkHttpClient;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@AllArgsConstructor
public class FileUploadConfig {

    private final StorageProperties storageProperties;

    @Bean
    public Cloudinary cloudinary() {
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
