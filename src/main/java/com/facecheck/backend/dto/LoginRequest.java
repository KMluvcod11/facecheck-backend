package com.facecheck.backend.dto;

import lombok.Data;

@Data
public class LoginRequest {
    private String username;   // ใช้ field เดียวกันทั้งนักศึกษาและอาจารย์
    private String password;
}
