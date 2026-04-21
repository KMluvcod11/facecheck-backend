package com.facecheck.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "leave_requests")
@Data
public class LeaveRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "student_id")
    private UUID studentId;          // UUID ของนักศึกษา (user.id)

    @Column(name = "class_id")
    private UUID classId;            // คลาสที่ขอลา

    @Column(name = "teacher_id")
    private UUID teacherId;          // อาจารย์เจ้าของวิชา

    @Column(name = "leave_type")
    private String leaveType;        // 'sick' = ลาป่วย, 'personal' = ลากิจ

    @Column(name = "leave_date")
    private LocalDate leaveDate;     // วันที่ขอลา

    @Column(columnDefinition = "text")
    private String reason;           // เหตุผลการลา

    private String status;           // 'pending', 'approved', 'rejected'

    @Column(name = "student_name")
    private String studentName;      // ชื่อนักศึกษา (เก็บไว้แสดงผลง่าย)

    @Column(name = "student_code")
    private String studentCode;      // รหัสนักศึกษา

    @Column(name = "subject_name")
    private String subjectName;      // ชื่อวิชา (เก็บไว้แสดงผลง่าย)

    @Column(name = "subject_code")
    private String subjectCode;      // รหัสวิชา

    @Column(name = "attachment_image", columnDefinition = "TEXT")
    private String attachmentImage;  // รูปแนบ (Base64 encoded)

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
