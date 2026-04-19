package com.facecheck.backend.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.facecheck.backend.entity.Attendance;

@Repository
public interface AttendanceRepository extends JpaRepository<Attendance, UUID> {

    // ค้นหาประวัติการเช็กชื่อของนักศึกษาคนนี้
    List<Attendance> findByStudentId(UUID studentId);

    // เพิ่มบรรทัดนี้ลงไป (ใช้สำหรับลบประวัติเช็คชื่อทั้งหมดของคลาสนี้)
    @org.springframework.transaction.annotation.Transactional
    void deleteByClassId(UUID classId);

    // ค้นหาประวัติการเช็คชื่อของนักศึกษาคนนี้ โดยเรียงจากเวลาเช็คชื่อล่าสุด (Desc)
    List<Attendance> findByStudentIdOrderByCheckedAtDesc(UUID studentId);

    // ค้นหาข้อมูลการเช็คชื่อตามคลาสและช่วงเวลา (สำหรับสถิติรายวัน)
    List<Attendance> findByClassIdAndCheckedAtBetween(UUID classId, java.time.LocalDateTime start, java.time.LocalDateTime end);

    // ค้นหาข้อมูลการเช็คชื่อทั้งหมดของคลาสนี้
    List<Attendance> findByClassId(UUID classId);

    // ✅ เพิ่มคำสั่งนี้เพื่อใช้คำนวณสถิติ (ดึงประวัตินักศึกษาเฉพาะวิชาที่เลือก)
    List<Attendance> findByStudentIdAndClassId(UUID studentId, UUID classId);
}