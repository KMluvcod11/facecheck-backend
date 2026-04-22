package com.facecheck.backend.controller;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.facecheck.backend.dto.LoginRequest;
import com.facecheck.backend.dto.RegisterRequest;
import com.facecheck.backend.entity.User;
import com.facecheck.backend.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    private UserRepository userRepository;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {
            // รองรับทั้ง field "username" (อาจารย์) และ "studentId" (นักศึกษา) จาก frontend
            String loginId = request.getUsername();
            if (loginId == null || loginId.isBlank()) {
                loginId = request.getStudentId();
            }
            Optional<User> userOpt = userRepository.findByUsername(loginId);

            if (userOpt.isPresent()) {
                User user = userOpt.get();
                // เช็กรหัสผ่าน (กัน Error null)
                if (request.getPassword() != null && request.getPassword().equals(user.getPasswordHash())) {
                    // สร้าง simple session token: Base64(userId:timestamp)
                    String raw = user.getId() + ":" + System.currentTimeMillis();
                    String token = Base64.getEncoder().encodeToString(raw.getBytes());

                    Map<String, Object> response = new HashMap<>();
                    response.put("message", "เข้าสู่ระบบสำเร็จ");
                    response.put("user", user);
                    response.put("token", token);  // ✅ frontend ใช้ field นี้
                    return ResponseEntity.ok(response);
                }
            }

            Map<String, String> error = new HashMap<>();
            error.put("message", "ข้อมูลเข้าสู่ระบบหรือรหัสผ่านไม่ถูกต้อง");
            return ResponseEntity.status(401).body(error);
        } catch (Exception e) {
            logger.error("Login failed", e);
            Map<String, String> error = new HashMap<>();
            error.put("message", "Internal System Error: " + e.toString());
            return ResponseEntity.status(500).body(error);
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        User newUser = new User();
        newUser.setUsername(request.getUsername()); // นักศึกษา = รหัสนักศึกษา, อาจารย์ = ตั้งเอง
        newUser.setPasswordHash(request.getPassword());
        newUser.setFullName(request.getFullName());
        newUser.setRole(request.getRole());

        // ล็อค studentId ให้ตรงกับ username สำหรับนักศึกษาเสมอ
        // อาจารย์ไม่มี studentId
        if ("student".equalsIgnoreCase(request.getRole())) {
            newUser.setStudentId(request.getUsername());
        } else {
            newUser.setStudentId(null);
        }

        // ✅ อัปเดต: แปลง List<List<Double>> เป็น String แบบ JSON
        if (request.getFaceDescriptor() != null && !request.getFaceDescriptor().isEmpty()) {
            try {
                // ใช้ ObjectMapper แปลงโครงสร้างที่ซับซ้อนให้เป็น String 
                ObjectMapper mapper = new ObjectMapper();
                String jsonDescriptor = mapper.writeValueAsString(request.getFaceDescriptor());
                newUser.setFaceDescriptor(jsonDescriptor);
            } catch (JsonProcessingException e) {
                logger.error("Failed to convert face descriptor", e);
                return ResponseEntity.status(500).body("เกิดข้อผิดพลาดในการแปลงข้อมูลใบหน้า");
            }
        } else {
            newUser.setFaceDescriptor(null); // ปล่อยให้ในฐานข้อมูลเป็นค่าว่าง (สำหรับอาจารย์)
        }

        try {
            userRepository.save(newUser);
            return ResponseEntity.ok("สมัครสมาชิกและบันทึกใบหน้าสำเร็จ");
        } catch (Exception e) {
            logger.error("Failed to save user to database", e);
            return ResponseEntity.status(500).body("เกิดข้อผิดพลาดในการเซฟลง Database");
        }
    }
}
