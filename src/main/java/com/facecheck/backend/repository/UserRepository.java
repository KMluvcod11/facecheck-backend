package com.facecheck.backend.repository;

import com.facecheck.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    // ให้ Spring Boot สร้างคำสั่งค้นหา User จาก Email ให้อัตโนมัติ (ใช้ตอน Login)
    Optional<User> findByEmail(String email);
}
