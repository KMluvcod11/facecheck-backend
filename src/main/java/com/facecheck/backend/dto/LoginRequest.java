package com.facecheck.backend.dto;

import lombok.Data;

@Data
public class LoginRequest {
    private String email;
    private String studentId; // เพิ่มบรรทัดนี้
    private String password;
}
