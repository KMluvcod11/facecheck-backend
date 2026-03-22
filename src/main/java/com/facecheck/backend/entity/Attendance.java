package com.facecheck.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "attendance")
@Data
public class Attendance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "class_id")
    private UUID classId;

    @Column(name = "student_id")
    private UUID studentId;

    @CreationTimestamp
    @Column(name = "checked_at")
    private LocalDateTime checkedAt;

    private String status; // 'present', 'late', 'absent'

    private Double latitude;
    private Double longitude;
}
