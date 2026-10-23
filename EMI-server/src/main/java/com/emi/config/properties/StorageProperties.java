package com.emi.config.properties;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "storage")
public class StorageProperties {

    private Cloudinary cloudinary=new Cloudinary();
    private Imagekit imagekit=new Imagekit();
    private Local local=new Local();

    // Document photos are kept on local disk, outside the web root; files are only served through the API
    @Data
    public static class Local{
        private String dir="uploads";
    }

    @Data
    public static class Cloudinary{
        private String url;
        private String cloudName;
        private String apiKey;
        private String apiSecret;
    }

    @Data
    public static class Imagekit{
        private String privateKey;
        private String webhookSecret;
    }
}
