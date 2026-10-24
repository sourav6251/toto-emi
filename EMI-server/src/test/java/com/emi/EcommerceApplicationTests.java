//package com.emi;
//
//import com.cloudinary.Cloudinary;
//import com.cloudinary.utils.ObjectUtils;
//import com.emi.adapter.out.storage.cloudinary.dto.FileUploadResponseDTO;
//import com.emi.application.port.out.notification.NotificationChannel;
//import com.emi.application.port.out.notification.NotificationPort;
//import com.emi.application.port.out.notification.NotificationRequest;
//import com.emi.application.port.out.storage.FileStoragePort;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.SpringBootConfiguration;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.core.io.ClassPathResource;
//import org.springframework.mock.web.MockMultipartFile;
//import org.springframework.test.context.ActiveProfiles;
//import org.springframework.web.multipart.MultipartFile;
//
//import java.io.IOException;
//
//@SpringBootTest
//@ActiveProfiles("dev")
//class EcommerceApplicationTests {
//
//    private final NotificationPort notificationPort;
//    private final FileStoragePort fileStoragePort;
//
//    @Autowired
//    public EcommerceApplicationTests(FileStoragePort fileStoragePort, NotificationPort notificationPort) {
//        this.fileStoragePort = fileStoragePort;
//        this.notificationPort = notificationPort;
//    }
//
//    @Test
//    void contextLoads() {
//    }
//
//    @Test
//    void testSendMail() {
//        notificationPort.send(NotificationRequest.builder()
//                .channel(NotificationChannel.EMAIL)
//                .recipient("dassourav3738@gmail.com")
//                .subject("Test Email from E-Commerce App")
//                .content("<h1>Hello Sourav!</h1><p>This is a test email sent from your Spring Boot E-Commerce application.</p>")
//                .build());
//    }
//
//    @Test
//    void testFileUpload() throws IOException {
//        // 1. Instantiate Cloudinary directly
//        // Cloudinary cloudinary = new Cloudinary(ObjectUtils.asMap(
//        //         "cloud_name", "dkxei4b5s",
//        //         "api_key", "676427825871499",
//        //         "api_secret", "N1IkkDZN72MT8imtldClpoNCUfQ",
//        //         "secure", true));
//
//        // FileStoragePort storage = new CloudinaryStorageAdapter(cloudinary);
//
//        // 2. Load test image
//        ClassPathResource resource = new ClassPathResource("images/jwt-hero.png");
//        MultipartFile file = new MockMultipartFile(
//                "file",
//                "jwt-hero.png",
//                "image/png",
//                resource.getInputStream()
//        );
//
//        // 3. Upload
//        FileUploadResponseDTO url = fileStoragePort.upload(file);
//        System.out.println("Uploaded URL: " + url);
//    }
//}
