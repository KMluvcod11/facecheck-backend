package com.facecheck.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalTime;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "classes")
@Data
public class ClassEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "teacher_id")
    private UUID teacherId; // ไอดีของอาจารย์ผู้สอน

    @Column(name = "subject_name")
    private String subjectName;

    @Column(name = "subject_code")
    private String subjectCode;

    private String room;
    
    @Column(name = "schedule_day")
    private String scheduleDay;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @Column(name = "late_threshold_minutes")
    private Integer lateThresholdMinutes;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    // เพิ่ม 3 บรรทัดนี้ต่อจากฟิลด์อื่นๆ ใน ClassEntity.java
    private Double latitude;
    private Double longitude;
    private Double radius; // รัศมีที่อนุญาตให้เช็คชื่อ (เมตร)
}
