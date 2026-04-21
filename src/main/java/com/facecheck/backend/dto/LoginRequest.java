package com.facecheck.backend.dto;

import lombok.Data;

@Data
public class LoginRequest {
    private String username;   // อาจารย์ใช้ field นี้
    private String studentId;  // นักศึกษาใช้ field นี้
    private String password;
}
