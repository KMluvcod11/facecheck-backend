package com.facecheck.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**") // อนุญาตให้ API ทั้งหมดที่ขึ้นต้นด้วย /api
                .allowedOrigins("http://localhost:5173", "http://localhost:5174", "https://facecheck-utcc.netlify.app") // อนุญาตให้ React ทั้ง local และ Netlify เข้าถึงได้
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
