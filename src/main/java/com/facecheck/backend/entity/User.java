package com.facecheck.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Data
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String email;

    @Column(unique = true)
    private String username;  // สำหรับอาจารย์ login
    
    @Column(name = "password_hash")
    private String passwordHash;
    
    @Column(name = "full_name")
    private String fullName;
    
    private String role; // 'student' หรือ 'instructor'
    
    @Column(name = "student_id")
    private String studentId;
    
    // เก็บเป็น String ไปก่อนเพื่อให้ง่ายต่อการรับ/ส่งข้อมูลใบหน้า
    @Column(name = "face_descriptor", columnDefinition = "text")
    private String faceDescriptor;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
