package com.emi.config;

//import com.emi.adapter.out.notification.properties.NotificationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

@Configuration
public class MailConfig {

//    private final NotificationProperties notificationProperties;
//
//    public MailConfig(NotificationProperties notificationProperties) {
//        this.notificationProperties = notificationProperties;
//    }
//
//    @Bean
//    public JavaMailSender javaMailSender() {
//
//        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
//
//        mailSender.setHost(notificationProperties.getMail().getHost());
//        mailSender.setPort(notificationProperties.getMail().getPort());
//
//        mailSender.setUsername(notificationProperties.getMail().getUsername());
//        mailSender.setPassword(notificationProperties.getMail().getPassword());
//
//        Properties properties = mailSender.getJavaMailProperties();
//
//        properties.put("mail.transport.protocol", notificationProperties.getMail().getProtocol());
//        properties.put("mail.smtp.auth", notificationProperties.getMail().getAuth());
//        properties.put("mail.debug", notificationProperties.getMail().getIsDebug());
//
//        if (notificationProperties.getMail().getPort() != null && notificationProperties.getMail().getPort() == 465) {
//            properties.put("mail.smtp.ssl.enable", "true");
//            properties.put("mail.smtp.socketFactory.port", "465");
//            properties.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
//        } else {
//            properties.put("mail.smtp.starttls.enable", notificationProperties.getMail().getIsTlsEnable());
//        }
//
//        return mailSender;
//    }

}
