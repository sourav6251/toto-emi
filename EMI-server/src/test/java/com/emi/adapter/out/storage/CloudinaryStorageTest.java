package com.emi.adapter.out.storage;

import com.emi.adapter.out.storage.cloudinary.CloudinaryStorageAdapter;
import com.emi.adapter.out.storage.cloudinary.dto.FileUploadResponseDTO;
import com.emi.application.port.out.storage.FileStoragePort;
import com.emi.config.FileUploadConfig;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {FileUploadConfig.class, CloudinaryStorageAdapter.class})
@TestPropertySource(locations = "classpath:application-dev.properties")
@ActiveProfiles("dev")
@Slf4j
class CloudinaryStorageTest {

    @Autowired
    private FileStoragePort fileStoragePort;

    @Value("${storage.url}")
    private String storageUrl;
    @Value("${storage.cloudName}")
    private String cloudName;
    @Value("${storage.api.key}")
    private String apiKey;
    @Value("${storage.api.secret}")
    private String apiSecret;

    @Test
    void testFileUploadToCloudinary()  {
        ClassPathResource resource = new ClassPathResource("images/jwt-hero.png");
        MultipartFile file=null;
        try{
            file = new MockMultipartFile(
                    "file",
                    "jwt-hero.png",
                    "image/png",
                    resource.getInputStream()
            );
        }catch (IOException e){
            log.error("File error= {}",e.getMessage());
        }
        FileUploadResponseDTO url = fileStoragePort.upload(file);
        System.out.println("=================================================");
        System.out.println("✅ Uploaded using FileUploadConfig: " + url);
        System.out.println("=================================================");

        assertNotNull(url);
        assertTrue(url.url().startsWith("https://res.cloudinary.com/"));

        log.info("File delete credential= {}",file.getOriginalFilename());
        fileStoragePort.delete(url.publicId());
    }
}

