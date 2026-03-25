package com.facecheck.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "notifications")
@Data
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;          // ส่งให้ใคร (UUID ของ user)

    @Column(name = "class_id")
    private UUID classId;         // เกี่ยวกับคลาสไหน

    private String type;          // 'warning', 'danger', 'info'
    private String title;
    
    @Column(columnDefinition = "text")
    private String message;

    @Column(name = "is_read")
    private Boolean isRead = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
