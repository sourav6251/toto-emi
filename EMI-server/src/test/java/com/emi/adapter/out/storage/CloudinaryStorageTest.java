package com.emi.adapter.out.storage;

import com.emi.config.FileUploadConfig;
import com.emi.config.properties.StorageProperties;
import com.emi.service.FileStorageService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
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
@ContextConfiguration(classes = {FileUploadConfig.class, FileStorageService.class})
@EnableConfigurationProperties(StorageProperties.class)
@TestPropertySource(locations = "classpath:application-dev.properties")
@ActiveProfiles("dev")
@Slf4j
class CloudinaryStorageTest {

    @Autowired
    private FileStorageService fileStorageService;

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
        FileStorageService.CloudinaryUploadResult result = fileStorageService.upload(file, "emi_documents");
        System.out.println("=================================================");
        System.out.println("✅ Uploaded using FileUploadConfig: " + result);
        System.out.println("=================================================");

        assertNotNull(result);
        assertTrue(result.secureUrl().startsWith("https://res.cloudinary.com/"));

        log.info("File delete credential= {}",file.getOriginalFilename());
        fileStorageService.delete(result.publicId());
    }
}

