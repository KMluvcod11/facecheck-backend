package com.facecheck.backend.dto;

import lombok.Data;
import java.util.List;

@Data
public class RegisterRequest {
    private String email;
    private String password;
    private String fullName;
    private String studentId;
    private String role;
    private List<List<Double>> faceDescriptor;
}
