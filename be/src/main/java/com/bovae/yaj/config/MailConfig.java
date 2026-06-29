package com.bovae.yaj.config;

import com.bovae.yaj.config.properties.SmtpProperties;
import java.util.Properties;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

@Configuration
public class MailConfig {

    @Bean
    public JavaMailSender javaMailSender(SmtpProperties properties) {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(properties.host());
        mailSender.setPort(properties.port());

        String username = properties.username();
        if (StringUtils.isNotBlank(username)) {
            mailSender.setUsername(username);
            mailSender.setPassword(properties.password());
        }

        // Bound every SMTP phase so a slow/hung relay cannot pin the dispatch thread (the
        // after-commit listener runs on the request thread) and exhaust the servlet pool.
        String timeoutMillis = Long.toString(properties.timeout().toMillis());
        Properties mailProperties = mailSender.getJavaMailProperties();
        mailProperties.setProperty("mail.smtp.connectiontimeout", timeoutMillis);
        mailProperties.setProperty("mail.smtp.timeout", timeoutMillis);
        mailProperties.setProperty("mail.smtp.writetimeout", timeoutMillis);

        return mailSender;
    }
}
