package com.facecheck.backend.repository;

import com.facecheck.backend.entity.Attendance;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface AttendanceRepository extends JpaRepository<Attendance, UUID> {
    // ค้นหาประวัติการเช็กชื่อของนักศึกษาคนนี้
    List<Attendance> findByStudentId(UUID studentId);
    // เพิ่มบรรทัดนี้ลงไป (ใช้สำหรับลบประวัติเช็คชื่อทั้งหมดของคลาสนี้)
    @org.springframework.transaction.annotation.Transactional
    void deleteByClassId(UUID classId);
    // ค้นหาประวัติการเช็คชื่อของนักศึกษาคนนี้ โดยเรียงจากเวลาเช็คชื่อล่าสุด (Desc)
    List<com.facecheck.backend.entity.Attendance> findByStudentIdOrderByCheckedAtDesc(UUID studentId);
    // ค้นหาข้อมูลการเช็คชื่อตามคลาสและช่วงเวลา (สำหรับสถิติรายวัน)
    List<Attendance> findByClassIdAndCheckedAtBetween(UUID classId, java.time.LocalDateTime start, java.time.LocalDateTime end);
}
