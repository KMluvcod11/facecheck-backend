package com.facecheck.backend.controller;

import com.facecheck.backend.dto.LoginRequest;
import com.facecheck.backend.entity.User;
import com.facecheck.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private UserRepository userRepository;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        // ค้นหาผู้ใช้จากอีเมลใน Database
        Optional<User> userOpt = userRepository.findByEmail(request.getEmail());

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

        // ถ้าอีเมลหรือรหัสผิด
        Map<String, String> error = new HashMap<>();
        error.put("message", "อีเมลหรือรหัสผ่านไม่ถูกต้อง");
        return ResponseEntity.status(401).body(error);
    }
}
