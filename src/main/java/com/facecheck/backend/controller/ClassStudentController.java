package com.facecheck.backend.controller;

import com.facecheck.backend.entity.ClassStudent;
import com.facecheck.backend.entity.User;
import com.facecheck.backend.repository.ClassStudentRepository;
import com.facecheck.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.transaction.Transactional;
import java.util.*;

@RestController
@RequestMapping("/api/class-students")
public class ClassStudentController {

    @Autowired
    private ClassStudentRepository classStudentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private com.facecheck.backend.repository.ClassRepository classRepository;

    // ==========================================
    // GET /api/class-students/{classId} — ดึงรายชื่อนักศึกษาในคลาส
    // ==========================================
    @GetMapping("/{classId}")
    public ResponseEntity<?> getStudentsByClass(@PathVariable UUID classId) {
        List<ClassStudent> classStudents = classStudentRepository.findByClassId(classId);

        // ดึงข้อมูล User ของแต่ละ studentId
        List<Map<String, Object>> result = new ArrayList<>();
        for (ClassStudent cs : classStudents) {
            var userOpt = userRepository.findById(cs.getStudentId());
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                Map<String, Object> studentData = new HashMap<>();
                studentData.put("id", cs.getId()); // id ของ class_students row
                studentData.put("studentUserId", user.getId()); // UUID ของ user
                studentData.put("studentId", user.getStudentId()); // รหัสนักศึกษา เช่น 2310511010014
                studentData.put("name", user.getFullName());
                studentData.put("email", user.getEmail());
                result.add(studentData);
            }
        }
        return ResponseEntity.ok(result);
    }

    // ==========================================
    // POST /api/class-students — เพิ่มนักศึกษาเข้าคลาส (ด้วยรหัสนักศึกษา)
    // ==========================================
    @PostMapping
    public ResponseEntity<?> addStudentToClass(@RequestBody Map<String, String> request) {
        try {
            UUID classId = UUID.fromString(request.get("classId"));
            String studentIdCode = request.get("studentId"); // รหัสนักศึกษา เช่น "2310511010014"

            // ค้นหา user จากรหัสนักศึกษา
            var userOpt = userRepository.findByStudentId(studentIdCode);
            if (userOpt.isEmpty()) {
                Map<String, String> error = new HashMap<>();
                error.put("message", "ไม่พบนักศึกษารหัส " + studentIdCode + " ในระบบ");
                return ResponseEntity.status(404).body(error);
            }

            User student = userOpt.get();

            // ตรวจสอบว่าเพิ่มแล้วหรือยัง
            var existing = classStudentRepository.findByClassIdAndStudentId(classId, student.getId());
            if (existing.isPresent()) {
                Map<String, String> error = new HashMap<>();
                error.put("message", "นักศึกษารหัส " + studentIdCode + " อยู่ในคลาสนี้แล้ว");
                return ResponseEntity.status(409).body(error);
            }

            // เพิ่มเข้าคลาส
            ClassStudent cs = new ClassStudent();
            cs.setClassId(classId);
            cs.setStudentId(student.getId());
            classStudentRepository.save(cs);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "เพิ่มนักศึกษาสำเร็จ");
            response.put("studentName", student.getFullName());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("message", "เพิ่มนักศึกษาไม่สำเร็จ: " + e.getMessage());
            return ResponseEntity.status(400).body(error);
        }
    }

    // ==========================================
    // DELETE /api/class-students/{classId}/{studentUserId} — ลบนักศึกษาออกจากคลาส
    // ==========================================
    @Transactional
    @DeleteMapping("/{classId}/{studentUserId}")
    public ResponseEntity<?> removeStudentFromClass(
            @PathVariable UUID classId,
            @PathVariable UUID studentUserId) {
        try {
            var existing = classStudentRepository.findByClassIdAndStudentId(classId, studentUserId);
            if (existing.isEmpty()) {
                Map<String, String> error = new HashMap<>();
                error.put("message", "ไม่พบนักศึกษาในคลาสนี้");
                return ResponseEntity.status(404).body(error);
            }
            classStudentRepository.deleteByClassIdAndStudentId(classId, studentUserId);
            Map<String, String> response = new HashMap<>();
            response.put("message", "ลบนักศึกษาออกจากคลาสสำเร็จ");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("message", "ลบนักศึกษาไม่สำเร็จ: " + e.getMessage());
            return ResponseEntity.status(400).body(error);
        }
    }

    // ==========================================
    // GET /api/class-students/student/{studentUserId} — ดึงคลาสทั้งหมดของนักศึกษา
    // ==========================================
    @GetMapping("/student/{studentUserId}")
    public ResponseEntity<?> getClassesForStudent(@PathVariable UUID studentUserId) {
        // ดึงรายการว่านักศึกษาคนนี้อยู่ในคลาสไหนบ้าง
        List<ClassStudent> enrollments = classStudentRepository.findByStudentId(studentUserId);

        List<Map<String, Object>> result = new ArrayList<>();
        for (ClassStudent cs : enrollments) {
            var classOpt = classRepository.findById(cs.getClassId());
            if (classOpt.isPresent()) {
                var c = classOpt.get();
                Map<String, Object> classData = new HashMap<>();
                classData.put("id", c.getId());
                classData.put("subjectCode", c.getSubjectCode());
                classData.put("subjectName", c.getSubjectName());
                classData.put("room", c.getRoom());
                classData.put("scheduleDay", c.getScheduleDay());
                classData.put("startTime", c.getStartTime() != null ? c.getStartTime().toString() : "");
                classData.put("endTime", c.getEndTime() != null ? c.getEndTime().toString() : "");
                result.add(classData);
            }
        }
        return ResponseEntity.ok(result);
    }

    // ==========================================
    // ✅ POST /api/class-students/join — นักศึกษากดเข้าร่วมคลาสเอง (ด้วยรหัสวิชา)
    // ==========================================
    @PostMapping("/join")
    public ResponseEntity<?> joinClassBySubjectCode(@RequestBody Map<String, String> request) {
        try {
            String subjectCode = request.get("subjectCode");
            String studentIdCode = request.get("studentId");

            // 1. หาวิชาจากรหัสวิชา
            List<com.facecheck.backend.entity.ClassEntity> classes = classRepository.findBySubjectCode(subjectCode);
            if (classes == null || classes.isEmpty()) {
                Map<String, String> error = new HashMap<>();
                error.put("message", "ไม่พบวิชารหัส '" + subjectCode + "' ในระบบ กรุณาตรวจสอบความถูกต้อง");
                return ResponseEntity.status(404).body(error);
            }
            com.facecheck.backend.entity.ClassEntity targetClass = classes.get(0); // เลือกวิชาแรกที่เจอ

            // 2. หานักศึกษา 2
            var userOpt = userRepository.findByStudentId(studentIdCode);
            if (userOpt.isEmpty()) {
                Map<String, String> error = new HashMap<>();
                error.put("message", "ไม่พบข้อมูลนักศึกษาของคุณในระบบ");
                return ResponseEntity.status(404).body(error);
            }
            User student = userOpt.get();

            // 3. เช็คว่าเคยเข้าร่วมหรือยัง
            var existing = classStudentRepository.findByClassIdAndStudentId(targetClass.getId(), student.getId());
            if (existing.isPresent()) {
                Map<String, String> error = new HashMap<>();
                error.put("message", "คุณอยู่ในคลาสวิชานี้เรียบร้อยแล้ว!");
                return ResponseEntity.status(409).body(error);
            }

            // 4. บันทึกเข้าคลาส
            ClassStudent cs = new ClassStudent();
            cs.setClassId(targetClass.getId());
            cs.setStudentId(student.getId());
            classStudentRepository.save(cs);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "เข้าร่วมคลาสสำเร็จ!");
            response.put("subjectName", targetClass.getSubjectName());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("message", "เกิดข้อผิดพลาดภายในระบบ: " + e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

}