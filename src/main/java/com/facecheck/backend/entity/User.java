package com.facecheck.backend.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * โครงสร้างฐานข้อมูลตาราง `users` (ตารางผู้ใช้งาน)
 * ใช้สำหรับเก็บข้อมูลบัญชีผู้ใช้ระบบ ทั้งสถานะ "นักศึกษา" และ "อาจารย์"
 */
@Entity
@Table(name = "users")
@Data
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true)
    private String username;  // นักศึกษา = รหัสนักศึกษา, อาจารย์ = ตั้งเอง
    
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
