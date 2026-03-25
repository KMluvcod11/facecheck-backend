package com.facecheck.backend.repository;

import com.facecheck.backend.entity.ClassStudent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClassStudentRepository extends JpaRepository<ClassStudent, UUID> {
    // ดึงนักศึกษาทั้งหมดในคลาส
    List<ClassStudent> findByClassId(UUID classId);

    // ตรวจสอบว่านักศึกษาอยู่ในคลาสแล้วหรือยัง
    Optional<ClassStudent> findByClassIdAndStudentId(UUID classId, UUID studentId);

    // ลบนักศึกษาออกจากคลาส
    void deleteByClassIdAndStudentId(UUID classId, UUID studentId);
}
