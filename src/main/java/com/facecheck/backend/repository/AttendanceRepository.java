package com.facecheck.backend.repository;

import com.facecheck.backend.entity.Attendance;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface AttendanceRepository extends JpaRepository<Attendance, UUID> {
    // ค้นหาประวัติการเช็กชื่อของนักศึกษาคนนี้
    List<Attendance> findByStudentId(UUID studentId);
}
