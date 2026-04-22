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

    /**
     * ระบบล็อกอินสำหรับทั้งนักศึกษาและอาจารย์
     * เมื่อล็อกอินสำเร็จ ระบบจะส่ง Token คืนไปให้เพื่อนำไปใช้งานอื่นๆ
     *
     * @param request ข้อมูลที่ส่งมา (Username และ Password)
     * @return ข้อมูลบัญชีผู้ใช้ พร้อมับ Token ควบคุมสิทธิ์
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {
            // 1. ค้นหาผู้ใช้จากระบบด้วย Username (นักศึกษา = รหัสนักศึกษา, อาจารย์ = ชื่อที่ตั้งอง)
            Optional<User> userOpt = userRepository.findByUsername(request.getUsername());

            if (userOpt.isPresent()) {
                User user = userOpt.get();
                // 2. เช็กรหัสผ่านว่าตรงกับในฐานข้อมูลหรือไม่
                if (request.getPassword() != null && request.getPassword().equals(user.getPasswordHash())) {
                    // 3. สร้าง Token สุ่มจำลอง (Base64 ของ userId + เวลาปัจจุบัน)
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

    /**
     * ระบบแสกนหน้าและสมัครสมาชิก (ลงทะเบียน) สำหรับนักศึกษาและอาจารย์
     * จะมีการแปลงข้อมูลกราฟใบหน้า (Face Descriptor) ให้เก็บลงฐานข้อมูลได้
     *
     * @param request ข้อมูลผู้สมัคร (ชื่อ, รหัส, บทบาท และ ข้อมูลใบหน้า ฯลฯ)
     * @return ข้อความยืนยันการสมัคร
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        // 1. สร้างพื้นที่เก็บข้อมูลสำหรับผู้ใช้ใหม่
        User newUser = new User();
        newUser.setUsername(request.getUsername()); // นักศึกษา = รหัสนักศึกษา, อาจารย์ = ตั้งเอง
        newUser.setPasswordHash(request.getPassword());
        newUser.setFullName(request.getFullName());
        newUser.setRole(request.getRole());

        // 2. จำกัดขอบเขต: หากเป็นนักศึกษา ให้ล๊อกพารามิเตอร์ studentId ให้ตรงกับ Username เสมอ
        // (ส่วนอาจารย์จะไม่ใช้ field นี้)
        if ("student".equalsIgnoreCase(request.getRole())) {
            newUser.setStudentId(request.getUsername());
        } else {
            newUser.setStudentId(null);
        }

        // 3. แปลงข้อมูล AI ใบหน้า (List<Double>) ให้อยู่ในฟอร์แมต String (JSON) เพื่อให้เซฟลง Database ได้
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
