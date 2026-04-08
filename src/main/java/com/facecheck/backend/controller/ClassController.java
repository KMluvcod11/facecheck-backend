package com.facecheck.backend.controller;

import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.facecheck.backend.dto.CreateClassRequest;
import com.facecheck.backend.entity.ClassEntity;
import com.facecheck.backend.repository.ClassRepository;

@RestController
@RequestMapping("/api/classes")
public class ClassController {
    @Autowired
    private com.facecheck.backend.repository.ClassStudentRepository classStudentRepository;

    @Autowired
    private com.facecheck.backend.repository.AttendanceRepository attendanceRepository;

    @Autowired
    private ClassRepository classRepository;

    @Autowired
    private com.facecheck.backend.repository.UserRepository userRepository;

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
            Integer lateThreshold = request.getLateThresholdMinutes();
            newClass.setLateThresholdMinutes(lateThreshold != null ? lateThreshold : 15);

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
    // PUT /api/classes/{id} — อัปเดตข้อมูลคลาส
    // ==========================================
    @PutMapping("/{id}")
    public ResponseEntity<?> updateClass(@PathVariable UUID id, @RequestBody CreateClassRequest request) {
        try {
            var classOpt = classRepository.findById(id);
            if (classOpt.isEmpty()) {
                Map<String, String> error = new HashMap<>();
                error.put("message", "ไม่พบคลาสนี้");
                return ResponseEntity.status(404).body(error);
            }

            ClassEntity existing = classOpt.get();

            if (request.getSubjectName() != null) existing.setSubjectName(request.getSubjectName());
            if (request.getSubjectCode() != null) existing.setSubjectCode(request.getSubjectCode());
            if (request.getRoom() != null) existing.setRoom(request.getRoom());
            if (request.getScheduleDay() != null) existing.setScheduleDay(request.getScheduleDay());
            if (request.getLateThresholdMinutes() != null) existing.setLateThresholdMinutes(request.getLateThresholdMinutes());
            if (request.getTerm() != null) existing.setTerm(request.getTerm());
            if (request.getInstructorName() != null) {
                existing.setInstructorName(request.getInstructorName());
                
                // อัปเดตโปรไฟล์อาจารย์ในบัญชีหลักด้วย (หน้าเว็บจะได้โชว์ชื่อตรงกันแม้ตอนล็อกอินใหม่)
                if (existing.getTeacherId() != null) {
                    var userOpt = userRepository.findById(existing.getTeacherId());
                    if (userOpt.isPresent()) {
                        com.facecheck.backend.entity.User updatedUser = userOpt.get();
                        updatedUser.setFullName(request.getInstructorName());
                        userRepository.save(updatedUser);
                    }
                }
            }

            if (request.getStartTime() != null && !request.getStartTime().isEmpty()) {
                existing.setStartTime(LocalTime.parse(request.getStartTime()));
            }
            if (request.getEndTime() != null && !request.getEndTime().isEmpty()) {
                existing.setEndTime(LocalTime.parse(request.getEndTime()));
            }

            ClassEntity saved = classRepository.save(existing);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "อัปเดตคลาสสำเร็จ");
            response.put("data", saved);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("message", "อัปเดตคลาสไม่สำเร็จ: " + e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    // ==========================================
    // DELETE /api/classes/{id} — ลบคลาส
    // ==========================================
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteClass(@PathVariable UUID id) {
        try {
            // 1. ลบประวัติการเข้าเรียนทั้งหมดของวิชานี้ทิ้งก่อน
            attendanceRepository.deleteByClassId(id);

            // 2. ลบรายชื่อนักศึกษาทั้งหมดที่ผูกกับวิชานี้ทิ้ง
            classStudentRepository.deleteByClassId(id);

            // 3. เมื่อไม่มีข้อมูลลูกค้างอยู่แล้ว ถึงจะลบคลาสหลักได้!
            classRepository.deleteById(id);

            Map<String, String> response = new HashMap<>();
            response.put("message", "ลบคลาสเรียนและข้อมูลที่เกี่ยวข้องสำเร็จ");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("message", "ลบคลาสไม่สำเร็จ: " + e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }
}
