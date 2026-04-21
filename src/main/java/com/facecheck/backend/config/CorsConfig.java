package com.facecheck.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                // อนุญาต local, Netlify และ Vercel (รวม preview deployments ทุกรูปแบบ) ให้เข้าถึงได้
                .allowedOriginPatterns(
                        "http://localhost:5173",
                        "http://localhost:5174",
                        "https://facecheck-utcc.netlify.app",
                        "https://*.vercel.app"
                )
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
