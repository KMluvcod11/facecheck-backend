package com.facecheck.backend.controller;

import com.facecheck.backend.dto.CreateClassRequest;
import com.facecheck.backend.entity.ClassEntity;
import com.facecheck.backend.repository.ClassRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/classes")
public class ClassController {

    @Autowired
    private ClassRepository classRepository;

    // ==========================================
    // POST /api/classes — สร้างคลาสใหม่
    // ==========================================
    @PostMapping
    public ResponseEntity<?> createClass(@RequestBody CreateClassRequest request) {
        try {
            ClassEntity newClass = new ClassEntity();
            newClass.setTeacherId(request.getTeacherId());
            newClass.setSubjectName(request.getSubjectName());
            newClass.setSubjectCode(request.getSubjectCode());
            newClass.setRoom(request.getRoom());
            newClass.setScheduleDay(request.getScheduleDay());
            newClass.setLateThresholdMinutes(
                request.getLateThresholdMinutes() != null ? request.getLateThresholdMinutes() : 15
            );

            // แปลง String "09:00" เป็น LocalTime
            if (request.getStartTime() != null && !request.getStartTime().isEmpty()) {
                newClass.setStartTime(LocalTime.parse(request.getStartTime()));
            }
            if (request.getEndTime() != null && !request.getEndTime().isEmpty()) {
                newClass.setEndTime(LocalTime.parse(request.getEndTime()));
            }

            ClassEntity saved = classRepository.save(newClass);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "สร้างคลาสสำเร็จ");
            response.put("data", saved);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("message", "ไม่สามารถสร้างคลาสได้: " + e.getMessage());
            return ResponseEntity.status(400).body(error);
        }
    }

    // ==========================================
    // GET /api/classes/teacher/{teacherId} — ดึงคลาสทั้งหมดของอาจารย์
    // ==========================================
    @GetMapping("/teacher/{teacherId}")
    public ResponseEntity<?> getClassesByTeacher(@PathVariable UUID teacherId) {
        List<ClassEntity> classes = classRepository.findByTeacherId(teacherId);
        return ResponseEntity.ok(classes);
    }

    // ==========================================
    // GET /api/classes/{id} — ดึงข้อมูลคลาสตาม ID
    // ==========================================
    @GetMapping("/{id}")
    public ResponseEntity<?> getClassById(@PathVariable UUID id) {
        var classOpt = classRepository.findById(id);
        if (classOpt.isPresent()) {
            return ResponseEntity.ok(classOpt.get());
        }
        Map<String, String> error = new HashMap<>();
        error.put("message", "ไม่พบคลาสนี้");
        return ResponseEntity.status(404).body(error);
    }

    // ==========================================
    // DELETE /api/classes/{id} — ลบคลาส
    // ==========================================
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteClass(@PathVariable UUID id) {
        try {
            if (!classRepository.existsById(id)) {
                Map<String, String> error = new HashMap<>();
                error.put("message", "ไม่พบคลาสที่ต้องการลบ");
                return ResponseEntity.status(404).body(error);
            }
            classRepository.deleteById(id);
            Map<String, String> response = new HashMap<>();
            response.put("message", "ลบคลาสสำเร็จ");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("message", "ไม่สามารถลบคลาสได้: " + e.getMessage());
            return ResponseEntity.status(400).body(error);
        }
    }
}
