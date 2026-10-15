package com.emi.adapter.out.storage;

import com.emi.adapter.out.storage.cloudinary.dto.FileUploadResponseDTO;
import com.emi.adapter.out.storage.imagekit.ImageKitAdapter;
import com.emi.application.port.out.storage.FileStoragePort;
import com.emi.config.FileUploadConfig;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {FileUploadConfig.class, ImageKitAdapter.class})
@TestPropertySource(locations = "classpath:application-dev.properties")
@ActiveProfiles("dev")
@Slf4j
class ImageKitStorageTest {

    @Autowired
    @Qualifier("imageKitAdapter")
    private FileStoragePort fileStoragePort;

    @Test
    void testFileUploadToImageKit()  {
        FileUploadResponseDTO url=null;
        String fileID=null;
        ClassPathResource resource = new ClassPathResource("images/jwt-hero.png");
        try {

            MultipartFile file = new MockMultipartFile(
                    "file",
                    "jwt-hero.png",
                    "image/png",
                    resource.getInputStream()
            );

            url = fileStoragePort.upload(file);
            System.out.println("=================================================");
            System.out.println("✅ ImageKit Uploaded URL: " + url);
            System.out.println("=================================================");

        }catch (IOException e) {
            log.error("Upload Error {}",e.getMessage());
        }

        assertNotNull(url);
        try{
            fileStoragePort.delete(url.publicId());
        }catch (Exception e){
            log.error("Delete Error {}",e.getMessage());
        }
    }
}

