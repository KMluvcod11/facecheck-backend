package com.facecheck.backend.controller;

import com.facecheck.backend.dto.LoginRequest;
import com.facecheck.backend.dto.RegisterRequest;
import com.facecheck.backend.entity.User;
import com.facecheck.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private UserRepository userRepository;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        Optional<User> userOpt;

        // เช็กว่ามีการส่งรหัสนักศึกษามาไหม (ถ้ามี แปลว่าเป็นนักศึกษา)
        if (request.getStudentId() != null && !request.getStudentId().isEmpty()) {
            userOpt = userRepository.findByStudentId(request.getStudentId());
        }
        // ถ้าไม่มี ส่งอีเมลมา (แปลว่าเป็นอาจารย์)
        else {
            userOpt = userRepository.findByEmail(request.getEmail());
        }

        if (userOpt.isPresent()) {
            User user = userOpt.get();
            // เช็กรหัสผ่าน (ชั่วคราวใช้เทียบ String ตรงๆ ไปก่อน)
            if (user.getPasswordHash().equals(request.getPassword())) {
                Map<String, Object> response = new HashMap<>();
                response.put("message", "เข้าสู่ระบบสำเร็จ");
                response.put("user", user);
                return ResponseEntity.ok(response);
            }
        }

        Map<String, String> error = new HashMap<>();
        error.put("message", "ข้อมูลเข้าสู่ระบบหรือรหัสผ่านไม่ถูกต้อง");
        return ResponseEntity.status(401).body(error);
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        User newUser = new User();
        newUser.setEmail(request.getEmail());
        newUser.setPasswordHash(request.getPassword()); // ในอนาคตควรใช้ BCrypt
        newUser.setFullName(request.getFullName());
        newUser.setStudentId(request.getStudentId());
        newUser.setRole(request.getRole());

        // ✅ อัปเดต: แปลง List<List<Double>> เป็น String แบบ JSON
        if (request.getFaceDescriptor() != null && !request.getFaceDescriptor().isEmpty()) {
            try {
                // ใช้ ObjectMapper แปลงโครงสร้างที่ซับซ้อนให้เป็น String 
                ObjectMapper mapper = new ObjectMapper();
                String jsonDescriptor = mapper.writeValueAsString(request.getFaceDescriptor());
                newUser.setFaceDescriptor(jsonDescriptor);
            } catch (Exception e) {
                e.printStackTrace();
                return ResponseEntity.status(500).body("เกิดข้อผิดพลาดในการแปลงข้อมูลใบหน้า");
            }
        } else {
            newUser.setFaceDescriptor(null); // ปล่อยให้ในฐานข้อมูลเป็นค่าว่าง (สำหรับอาจารย์)
        }

        try {
            userRepository.save(newUser);
            return ResponseEntity.ok("สมัครสมาชิกและบันทึกใบหน้าสำเร็จ");
        } catch (Exception e) {
            e.printStackTrace(); 
            return ResponseEntity.status(500).body("เกิดข้อผิดพลาดในการเซฟลง Database");
        }
    }
}
