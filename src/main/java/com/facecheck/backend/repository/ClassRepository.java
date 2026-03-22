package com.facecheck.backend.repository;

import com.facecheck.backend.entity.ClassEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ClassRepository extends JpaRepository<ClassEntity, UUID> {
    // ค้นหาวิชาทั้งหมดที่อาจารย์คนนี้สอน
    List<ClassEntity> findByTeacherId(UUID teacherId);
}
