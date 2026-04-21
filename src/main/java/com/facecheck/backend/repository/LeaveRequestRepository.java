package com.facecheck.backend.repository;

import com.facecheck.backend.entity.LeaveRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, UUID> {

    // ดึงใบลาตาม classId (สำหรับอาจารย์ดู)
    List<LeaveRequest> findByClassIdOrderByCreatedAtDesc(UUID classId);

    // ดึงใบลาที่ยังรออนุมัติ ตาม teacherId
    List<LeaveRequest> findByTeacherIdAndStatusOrderByCreatedAtDesc(UUID teacherId, String status);

    // ดึงใบลาทั้งหมดของอาจารย์
    List<LeaveRequest> findByTeacherIdOrderByCreatedAtDesc(UUID teacherId);

    // ดึงใบลาของนักศึกษาตาม classId
    List<LeaveRequest> findByStudentIdAndClassIdOrderByCreatedAtDesc(UUID studentId, UUID classId);

    // ดึงใบลาของนักศึกษา (ทุกวิชา)
    List<LeaveRequest> findByStudentIdOrderByCreatedAtDesc(UUID studentId);
}
