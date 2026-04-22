package com.facecheck.backend.dto;

import lombok.Data;

@Data
public class LoginRequest {
    private String username;   // สำหรับอาจารย์ หรือถ้า frontend ส่งชื่อ field นี้
    private String studentId;  // สำหรับ frontend ที่ส่ง field นี้ (tab นักศึกษา)
    private String password;
}
