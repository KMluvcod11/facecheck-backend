package com.facecheck.backend.repository;

import com.facecheck.backend.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    // ดึงการแจ้งเตือนของ user เรียงจากใหม่สุด
    List<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId);

    // นับจำนวนที่ยังไม่อ่าน
    long countByUserIdAndIsReadFalse(UUID userId);
}
