package com.facecheck.backend.repository;

import com.facecheck.backend.entity.ClassEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ClassRepository extends JpaRepository<ClassEntity, UUID> {

    // ของเดิม: ค้นหาวิชาทั้งหมดที่อาจารย์คนนี้สอน (ห้ามลบ)
    List<ClassEntity> findByTeacherId(UUID teacherId);

    // ของใหม่: ค้นหาวิชาจากรหัสวิชา (สำหรับให้นักศึกษากดเข้าร่วม)
    List<ClassEntity> findBySubjectCode(String subjectCode);
}