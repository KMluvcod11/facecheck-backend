package com.facecheck.backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api") // Base URL สำหรับ API ทั้งหมดในคลาสนี้
public class TestController {

    /**
     * API ทดสอบการเชื่อมต่อ
     * เอาไว้เปิดในเบราว์เซอร์ดูว่าเซิร์ฟเวอร์รันติดและพร้อมทำงานหรือยัง
     *
     * @return ข้อความสวัสดี
     */
    @GetMapping("/hello")
    public Map<String, String> sayHello() {
        Map<String, String> response = new HashMap<>();
        response.put("message", "สวัสดี! สปริงบูตเชื่อมต่อกับรีแอคสำเร็จแล้ว 🎉");
        response.put("status", "success");
        return response;
    }
}
