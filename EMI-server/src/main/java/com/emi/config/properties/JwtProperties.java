package com.emi.config.properties;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "app")
public class JwtProperties {

    private Jwt jwt=new Jwt();
    private Cors cors=new Cors();
    public String applicationMode;

    @Data
    public static class Jwt{
        private String secret;
        private Long expirationMs;
    }

    @Data
    public static class Cors{
        private List<String> allowedOrigins;
    }

}
