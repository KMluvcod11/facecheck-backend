package com.facecheck.backend.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.facecheck.backend.entity.User;

/**
 * อินเทอร์เฟสสำหรับการติดต่อฐานข้อมูล (Query) ไปยังตาราง `users`
 * สามารถใช้ค้นหาข้อมูลผู้ใช้ได้จาก Username หรือ StudentId
 */
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByStudentId(String studentId);
    Optional<User> findByUsername(String username);
}
