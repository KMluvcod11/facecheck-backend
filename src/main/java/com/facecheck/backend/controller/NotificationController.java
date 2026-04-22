package com.facecheck.backend.controller;

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

import com.facecheck.backend.entity.ClassEntity;
import com.facecheck.backend.entity.ClassStudent;
import com.facecheck.backend.entity.Notification;
import com.facecheck.backend.repository.ClassRepository;
import com.facecheck.backend.repository.ClassStudentRepository;
import com.facecheck.backend.repository.NotificationRepository;
import com.fasterxml.jackson.core.JsonProcessingException;

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

    /**
     * ดึงข้อมูลการแจ้งเตือน (กระดิ่ง) ของผู้ใช้คนนั้นๆ ทั้งหมด
     * (ดึงใบลา ดึงแจ้งเตือนยกคลาส ดึงเตะออกจากคลาส ฯลฯ)
     *
     * @param userId รหัส UUID ของผู้ใช้ (นักศึกษา หรือ อาจารย)
     * @return รายการแจ้งเตือนทั้งหมด เรียงจากใหม่ไปเก่า
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getUserNotifications(@PathVariable UUID userId) {
        List<Notification> notifications = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return ResponseEntity.ok(notifications);
    }

    // ==========================================
    // PUT /api/notifications/{id}/read — อัพเดทว่าอ่านแล้ว
    // ==========================================

    /**
     * เปลี่ยนสถานะการแจ้งเตือน 1 ชิ้นว่า "อ่านแล้ว" (Read) เพื่อให้จุดแดงหายไป
     *
     * @param id รหัส UUID ของการแจ้งเตือน
     * @return อัปเดตสำเร็จหรือไม่
     */
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

    /**
     * ผู้ใช้กดปุ่ม "อ่านทั้งหมด" ระบบจะกวาดล้างจุดแดงของการแจ้งเตือนทุกชิ้น
     *
     * @param userId รหัส UUID ของผู้ใช้
     * @return จำนวนหรือข้อความสำเร็จ
     */
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

    /**
     * ผู้ใช้เลือก "ลบ" การแจ้งเตือนนี้ทิ้งจากกล่องจดหมายถาวร
     *
     * @param id รหัส UUID ของการแจ้งเตือน
     * @return ลบสำเร็จหรือไม่
     */
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

    /**
     * ฟังก์ชันพิเศษ: สำหรับอาจารย์กด "ยกเลิกคลาส" ของวันนี้
     * ระบบจะลบวันที่ออกจากปฏิทิน และส่งแจ้งเตือนสีแดงให้นักศึกษาทุกคนรู้
     *
     * @param classId รหัส UUID ของคลาสที่จะยกเลิก
     * @return ส่งแจ้งเตือนสำเร็จกี่คน
     */
    @PostMapping("/cancel-class/{classId}")
    public ResponseEntity<?> notifyCancelClass(@PathVariable UUID classId) {
        try {
            var classOpt = classRepository.findById(classId);
            if (classOpt.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("message", "ไม่พบคลาสนี้"));
            }
            ClassEntity classEntity = classOpt.get();

            // ✅ ลบวันนี้ออกจาก scheduledDates เพื่อปิดการสแกนของนักศึกษาทันที
            String todayStr = java.time.LocalDate.now().toString(); // "2026-04-20"
            String currentDates = classEntity.getScheduledDates();
            if (currentDates != null && !currentDates.isEmpty()) {
                try {
                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    com.fasterxml.jackson.databind.node.ArrayNode datesArray = (com.fasterxml.jackson.databind.node.ArrayNode) mapper.readTree(currentDates);
                    com.fasterxml.jackson.databind.node.ArrayNode filteredDates = mapper.createArrayNode();
                    for (var node : datesArray) {
                        if (!todayStr.equals(node.get("date").asText())) {
                            filteredDates.add(node);
                        }
                    }
                    classEntity.setScheduledDates(mapper.writeValueAsString(filteredDates));
                    classRepository.save(classEntity);
                } catch (JsonProcessingException | ClassCastException jsonEx) {
                    // ถ้า parse JSON ไม่ได้ ก็ข้ามไป (ไม่กระทบการส่งแจ้งเตือน)
                    System.err.println("ไม่สามารถอัปเดต scheduledDates: " + jsonEx.getMessage());
                }
            }

            // 2. ดึงรายชื่อนักศึกษาในห้อง เพื่อเตรียมส่งแจ้งเตือนรายบุคคล
            List<ClassStudent> students = classStudentRepository.findByClassId(classId);
            if (students.isEmpty()) {
                return ResponseEntity.ok(Map.of("message", "ยกเลิกคลาสแล้ว แต่ไม่มีนักศึกษาในคลาสนี้ให้แจ้งเตือน"));
            }

            // 3. วนลูปส่งการแจ้งเตือนสีแดงให้นักศึกษาทีละคน
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

            return ResponseEntity.ok(Map.of("message", "ยกเลิกคลาสสำเร็จ ลบวันนี้ออกจากตารางแล้ว และส่งแจ้งเตือนให้นักศึกษา " + count + " คน"));
        } catch (RuntimeException e) {
            return ResponseEntity.status(400).body(Map.of("message", "ส่งแจ้งเตือนไม่สำเร็จ: " + e.getMessage()));
        }
    }

    // ==========================================
    // ✅ POST /api/notifications/start-checkin/{classId} — แจ้งเตือนเริ่มเช็คชื่อ
    // ==========================================

    /**
     * ฟังก์ชันพิเศษ: สำหรับอาจารย์กด "เริ่มเช็คชื่อ"
     * ระบบออโต้จะยิงแจ้งเตือนเด้งไปหานักศึกษาทุกคนให้เตรียมตัวส่งหน้าสแกน
     *
     * @param classId รหัส UUID ของวิชาที่เปิดเช็คชื่อ
     * @return จำนวนคนที่ได้รับแจ้งเตือน
     */
    @PostMapping("/start-checkin/{classId}")
    public ResponseEntity<?> notifyStartCheckIn(@PathVariable UUID classId) {
        try {
            var classOpt = classRepository.findById(classId);
            if (classOpt.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("message", "ไม่พบคลาสนี้"));
            }
            ClassEntity classEntity = classOpt.get();

            // 1. ดึงนักศึกษาทั้งหมดในคลาส
            List<ClassStudent> students = classStudentRepository.findByClassId(classId);
            if (students.isEmpty()) {
                return ResponseEntity.ok(Map.of("message", "ไม่มีนักศึกษาในคลาสนี้ให้แจ้งเตือน"));
            }

            // 2. ส่ง Notification แจ้งเตือนสีฟ้าไปยังแต่ละคน
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

    /**
     * เปิดช่องทาง API ให้ออโต้บอท (AI/n8n) ยิงแจ้งเตือนเตือนนักศึกษา
     * ว่าขาดเรียนบ่อย หรือใกล้หมดสิทธิ์สอบ
     *
     * @param payload ข้อมูลที่รับมาประกอบด้วย รหัสนักศึกษา (studentUserId) และข้อความ (message)
     * @return ผลการยิงแจ้งเตือน 
     */
    @PostMapping("/ai-alert")
    public ResponseEntity<?> sendAiAlert(@RequestBody Map<String, Object> payload) {
        try {
            UUID studentUserId = UUID.fromString(payload.get("studentUserId").toString());
            String message = payload.get("message").toString();

            Notification notif = new Notification();
            notif.setUserId(studentUserId);
            notif.setType("danger"); // ให้เป็นสีแดง (เตือนอันตราย)
            notif.setTitle("⚠️ แจ้งเตือนความเสี่ยงการเข้าเรียน");
            notif.setMessage(message);
            notif.setIsRead(false);

            notificationRepository.save(notif);

            return ResponseEntity.ok(Map.of("message", "ส่งการแจ้งเตือนเข้าระบบนักศึกษาสำเร็จ"));
        } catch (Exception e) {
            return ResponseEntity.status(400).body(Map.of("message", "ส่งแจ้งเตือนไม่สำเร็จ: " + e.getMessage()));
        }
    }
}