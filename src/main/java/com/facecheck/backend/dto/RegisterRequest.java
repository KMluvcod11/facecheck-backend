package com.facecheck.backend.dto;

import lombok.Data;
import java.util.List;

@Data
public class RegisterRequest {
    private String username;   // อาจารย์ใช้ username แทน email
    private String email;      // เก็บไว้สำหรับนักศึกษา (สร้างจาก studentId@utcc.ac.th)
    private String password;
    private String fullName;
    private String studentId;
    private String role;
    private List<List<Double>> faceDescriptor;
}
