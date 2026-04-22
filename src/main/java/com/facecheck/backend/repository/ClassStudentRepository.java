package com.facecheck.backend.repository;

import com.facecheck.backend.entity.ClassStudent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClassStudentRepository extends JpaRepository<ClassStudent, UUID> {

    // ค้นหานักศึกษาทั้งหมดที่อยู่ในคลาสนี้
    List<ClassStudent> findByClassId(UUID classId);

    // ค้นหาว่านักศึกษาคนนี้อยู่ในคลาสนี้หรือยัง
    Optional<ClassStudent> findByClassIdAndStudentId(UUID classId, UUID studentId);

    // ลบนักศึกษาออกจากคลาส
    void deleteByClassIdAndStudentId(UUID classId, UUID studentId);

    // บรรทัดที่เราเพิ่มเข้ามาใหม่ (ใช้หาว่านักศึกษาคนนี้ ลงเรียนคลาสไหนบ้าง)
    List<ClassStudent> findByStudentId(UUID studentId);

    // เพิ่มบรรทัดนี้ลงไป (ใช้สำหรับลบเด็กทุกคนออกจากคลาส)
    @org.springframework.transaction.annotation.Transactional
    void deleteByClassId(UUID classId);
}