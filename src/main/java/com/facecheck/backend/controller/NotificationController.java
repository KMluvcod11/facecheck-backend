package com.facecheck.backend.controller;

import com.facecheck.backend.entity.ClassEntity;
import com.facecheck.backend.entity.ClassStudent;
import com.facecheck.backend.entity.Notification;
import com.facecheck.backend.repository.ClassRepository;
import com.facecheck.backend.repository.ClassStudentRepository;
import com.facecheck.backend.repository.NotificationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private ClassRepository classRepository;

    @Autowired
    private ClassStudentRepository classStudentRepository;

    // ==========================================
    // GET /api/notifications/user/{userId} — ดึงแจ้งเตือนของ User
    // ==========================================
    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getUserNotifications(@PathVariable UUID userId) {
        List<Notification> notifications = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return ResponseEntity.ok(notifications);
    }

    // ==========================================
    // PUT /api/notifications/{id}/read — อัพเดทว่าอ่านแล้ว
    // ==========================================
    @PutMapping("/{id}/read")
    public ResponseEntity<?> markAsRead(@PathVariable UUID id) {
        var notifOpt = notificationRepository.findById(id);
        if (notifOpt.isPresent()) {
            Notification notif = notifOpt.get();
            notif.setIsRead(true);
            notificationRepository.save(notif);
            return ResponseEntity.ok(Map.of("message", "ทำเครื่องหมายว่าอ่านแล้ว"));
        }
        return ResponseEntity.status(404).body(Map.of("message", "ไม่พบการแจ้งเตือน"));
    }

    // ==========================================
    // PUT /api/notifications/user/{userId}/read-all — อ่านทั้งหมด
    // ==========================================
    @PutMapping("/user/{userId}/read-all")
    public ResponseEntity<?> markAllAsRead(@PathVariable UUID userId) {
        List<Notification> notifications = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
        for (Notification n : notifications) {
            if (!n.getIsRead()) {
                n.setIsRead(true);
                notificationRepository.save(n);
            }
        }
        return ResponseEntity.ok(Map.of("message", "ทำเครื่องหมายว่าอ่านแล้วทั้งหมด"));
    }

    // ==========================================
    // DELETE /api/notifications/{id} — ลบแจ้งเตือน
    // ==========================================
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteNotification(@PathVariable UUID id) {
        if (!notificationRepository.existsById(id)) {
            return ResponseEntity.status(404).body(Map.of("message", "ไม่พบการแจ้งเตือน"));
        }
        notificationRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "ลบการแจ้งเตือนสำเร็จ"));
    }

    // ==========================================
    // POST /api/notifications/cancel-class/{classId} — แจ้งเตือนยกคลาส
    // ==========================================
    @PostMapping("/cancel-class/{classId}")
    public ResponseEntity<?> notifyCancelClass(@PathVariable UUID classId) {
        try {
            var classOpt = classRepository.findById(classId);
            if (classOpt.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("message", "ไม่พบคลาสนี้"));
            }
            ClassEntity classEntity = classOpt.get();

            List<ClassStudent> students = classStudentRepository.findByClassId(classId);
            if (students.isEmpty()) {
                return ResponseEntity.ok(Map.of("message", "ไม่มีนักศึกษาในคลาสนี้ให้แจ้งเตือน"));
            }

            int count = 0;
            for (ClassStudent cs : students) {
                Notification notif = new Notification();
                notif.setUserId(cs.getStudentId());
                notif.setClassId(classId);
                notif.setType("danger");
                notif.setTitle("แจ้งยกเลิกคลาสเรียน");
                notif.setMessage("อาจารย์แจ้งยกเลิกคลาสเรียนวิชา " + classEntity.getSubjectCode() + " " + classEntity.getSubjectName() + " สำหรับวันนี้");
                notif.setIsRead(false);
                notificationRepository.save(notif);
                count++;
            }

            return ResponseEntity.ok(Map.of("message", "ส่งแจ้งเตือนให้นักศึกษา " + count + " คน สำเร็จ"));
        } catch (Exception e) {
            return ResponseEntity.status(400).body(Map.of("message", "ส่งแจ้งเตือนไม่สำเร็จ: " + e.getMessage()));
        }
    }

    // ==========================================
    // ✅ POST /api/notifications/start-checkin/{classId} — แจ้งเตือนเริ่มเช็คชื่อ
    // ==========================================
    @PostMapping("/start-checkin/{classId}")
    public ResponseEntity<?> notifyStartCheckIn(@PathVariable UUID classId) {
        try {
            var classOpt = classRepository.findById(classId);
            if (classOpt.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("message", "ไม่พบคลาสนี้"));
            }
            ClassEntity classEntity = classOpt.get();

            List<ClassStudent> students = classStudentRepository.findByClassId(classId);
            if (students.isEmpty()) {
                return ResponseEntity.ok(Map.of("message", "ไม่มีนักศึกษาในคลาสนี้ให้แจ้งเตือน"));
            }

            int count = 0;
            for (ClassStudent cs : students) {
                Notification notif = new Notification();
                notif.setUserId(cs.getStudentId());
                notif.setClassId(classId);
                // ใช้ type "info" (สีฟ้า) สำหรับการแจ้งเตือนเริ่มเช็คชื่อ
                notif.setType("info");
                notif.setTitle("เริ่มเช็คชื่อแล้ว!");
                notif.setMessage("อาจารย์เปิดระบบเช็คชื่อวิชา " + classEntity.getSubjectCode() + " " + classEntity.getSubjectName() + " แล้ว กรุณาเข้าเช็คชื่อ");
                notif.setIsRead(false);
                notificationRepository.save(notif);
                count++;
            }

            return ResponseEntity.ok(Map.of("message", "ส่งแจ้งเตือนเริ่มเช็คชื่อให้นักศึกษา " + count + " คน สำเร็จ"));
        } catch (Exception e) {
            return ResponseEntity.status(400).body(Map.of("message", "ส่งแจ้งเตือนไม่สำเร็จ: " + e.getMessage()));
        }
    }
    // ==========================================
    // ✅ POST /api/notifications/ai-alert — ส่งแจ้งเตือน AI เข้าระบบนักศึกษาแบบเจาะจง
    // ==========================================
    @PostMapping("/ai-alert")
    public ResponseEntity<?> sendAiAlert(@RequestBody Map<String, Object> payload) {
        try {
            UUID studentUserId = UUID.fromString(payload.get("studentUserId").toString());
            String message = payload.get("message").toString();

            Notification notif = new Notification();
            notif.setUserId(studentUserId);
            notif.setType("danger"); // ให้เป็นสีแดง (เตือนอันตราย)
            notif.setTitle("⚠️ แจ้งเตือนความเสี่ยงหมดสิทธิ์สอบ");
            notif.setMessage(message);
            notif.setIsRead(false);

            notificationRepository.save(notif);

            return ResponseEntity.ok(Map.of("message", "ส่งการแจ้งเตือนเข้าระบบนักศึกษาสำเร็จ"));
        } catch (Exception e) {
            return ResponseEntity.status(400).body(Map.of("message", "ส่งแจ้งเตือนไม่สำเร็จ: " + e.getMessage()));
        }
    }
}