package com.facecheck.backend.dto;

import java.util.List;

import lombok.Data;

@Data
public class RegisterRequest {
    private String username;   // นักศึกษา = รหัสนักศึกษา, อาจารย์ = ตั้งเอง
    private String password;
    private String fullName;
    private String studentId;
    private String role;
    private List<List<Double>> faceDescriptor;
}
