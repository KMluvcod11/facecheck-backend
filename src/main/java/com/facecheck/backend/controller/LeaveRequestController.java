package com.facecheck.backend.controller;

import com.facecheck.backend.entity.ClassEntity;
import com.facecheck.backend.entity.LeaveRequest;
import com.facecheck.backend.entity.Notification;
import com.facecheck.backend.entity.User;
import com.facecheck.backend.repository.ClassRepository;
import com.facecheck.backend.repository.LeaveRequestRepository;
import com.facecheck.backend.repository.NotificationRepository;
import com.facecheck.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/leave-requests")
public class LeaveRequestController {

    @Autowired
    private LeaveRequestRepository leaveRequestRepository;

    @Autowired
    private ClassRepository classRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    // ==========================================
    // POST /api/leave-requests — นักศึกษาส่งใบลา
    // ==========================================
    @PostMapping
    public ResponseEntity<?> createLeaveRequest(@RequestBody Map<String, Object> payload) {
        try {
            UUID studentId = UUID.fromString(payload.get("studentId").toString());
            UUID classId = UUID.fromString(payload.get("classId").toString());
            String leaveType = payload.get("leaveType").toString();    // 'sick' หรือ 'personal'
            String leaveDate = payload.get("leaveDate").toString();    // 'YYYY-MM-DD'
            String reason = payload.getOrDefault("reason", "").toString();
            String attachmentImage = payload.containsKey("attachmentImage") ? payload.get("attachmentImage").toString() : null;

            // หา classEntity
            var classOpt = classRepository.findById(classId);
            if (classOpt.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("message", "ไม่พบคลาสนี้"));
            }
            ClassEntity classEntity = classOpt.get();

            // หา user (นักศึกษา)
            var userOpt = userRepository.findById(studentId);
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("message", "ไม่พบนักศึกษา"));
            }
            User student = userOpt.get();

            // สร้าง LeaveRequest
            LeaveRequest lr = new LeaveRequest();
            lr.setStudentId(studentId);
            lr.setClassId(classId);
            lr.setTeacherId(classEntity.getTeacherId());
            lr.setLeaveType(leaveType);
            lr.setLeaveDate(LocalDate.parse(leaveDate));
            lr.setReason(reason);
            lr.setStatus("pending");
            lr.setStudentName(student.getFullName());
            lr.setStudentCode(student.getStudentId());
            lr.setSubjectName(classEntity.getSubjectName());
            lr.setSubjectCode(classEntity.getSubjectCode());
            lr.setAttachmentImage(attachmentImage);

            leaveRequestRepository.save(lr);

            return ResponseEntity.ok(Map.of(
                    "message", "ส่งใบลาเรียบร้อยแล้ว",
                    "leaveRequest", lr
            ));
        } catch (Exception e) {
            return ResponseEntity.status(400).body(Map.of("message", "ส่งใบลาไม่สำเร็จ: " + e.getMessage()));
        }
    }

    // ==========================================
    // GET /api/leave-requests/teacher/{teacherId} — อาจารย์ดูใบลาทั้งหมด
    // ==========================================
    @GetMapping("/teacher/{teacherId}")
    public ResponseEntity<?> getLeaveRequestsByTeacher(@PathVariable UUID teacherId) {
        List<LeaveRequest> requests = leaveRequestRepository.findByTeacherIdOrderByCreatedAtDesc(teacherId);
        return ResponseEntity.ok(requests);
    }

    // ==========================================
    // GET /api/leave-requests/teacher/{teacherId}/pending — อาจารย์ดูใบลาที่รอ
    // ==========================================
    @GetMapping("/teacher/{teacherId}/pending")
    public ResponseEntity<?> getPendingRequests(@PathVariable UUID teacherId) {
        List<LeaveRequest> requests = leaveRequestRepository.findByTeacherIdAndStatusOrderByCreatedAtDesc(teacherId, "pending");
        return ResponseEntity.ok(requests);
    }

    // ==========================================
    // GET /api/leave-requests/class/{classId} — ดูใบลาตามวิชา
    // ==========================================
    @GetMapping("/class/{classId}")
    public ResponseEntity<?> getLeaveRequestsByClass(@PathVariable UUID classId) {
        List<LeaveRequest> requests = leaveRequestRepository.findByClassIdOrderByCreatedAtDesc(classId);
        return ResponseEntity.ok(requests);
    }

    // ==========================================
    // GET /api/leave-requests/student/{studentId} — นักศึกษาดูใบลาของตัวเอง
    // ==========================================
    @GetMapping("/student/{studentId}")
    public ResponseEntity<?> getLeaveRequestsByStudent(@PathVariable UUID studentId) {
        List<LeaveRequest> requests = leaveRequestRepository.findByStudentIdOrderByCreatedAtDesc(studentId);
        return ResponseEntity.ok(requests);
    }

    // ==========================================
    // PUT /api/leave-requests/{id}/approve — อนุมัติใบลา
    // ==========================================
    @PutMapping("/{id}/approve")
    public ResponseEntity<?> approveLeaveRequest(@PathVariable UUID id) {
        var opt = leaveRequestRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("message", "ไม่พบใบลา"));
        }
        LeaveRequest lr = opt.get();
        lr.setStatus("approved");
        lr.setUpdatedAt(LocalDateTime.now());
        leaveRequestRepository.save(lr);

        // ส่ง Notification แจ้งนักศึกษา
        String leaveLabel = lr.getLeaveType().equals("sick") ? "ลาป่วย" : "ลากิจ";
        Notification notif = new Notification();
        notif.setUserId(lr.getStudentId());
        notif.setClassId(lr.getClassId());
        notif.setType("info");
        notif.setTitle("✅ ใบ" + leaveLabel + "ได้รับการอนุมัติแล้ว");
        notif.setMessage("อาจารย์อนุมัติใบ" + leaveLabel + " วิชา " + lr.getSubjectCode() + " " + lr.getSubjectName()
                + " วันที่ " + lr.getLeaveDate().toString() + " เรียบร้อยแล้ว");
        notif.setIsRead(false);
        notificationRepository.save(notif);

        return ResponseEntity.ok(Map.of("message", "อนุมัติใบลาเรียบร้อยแล้ว"));
    }

    // ==========================================
    // PUT /api/leave-requests/{id}/reject — ปฏิเสธใบลา
    // ==========================================
    @PutMapping("/{id}/reject")
    public ResponseEntity<?> rejectLeaveRequest(@PathVariable UUID id) {
        var opt = leaveRequestRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("message", "ไม่พบใบลา"));
        }
        LeaveRequest lr = opt.get();
        lr.setStatus("rejected");
        lr.setUpdatedAt(LocalDateTime.now());
        leaveRequestRepository.save(lr);

        // ส่ง Notification แจ้งนักศึกษา
        String leaveLabel = lr.getLeaveType().equals("sick") ? "ลาป่วย" : "ลากิจ";
        Notification notif = new Notification();
        notif.setUserId(lr.getStudentId());
        notif.setClassId(lr.getClassId());
        notif.setType("danger");
        notif.setTitle("❌ ใบ" + leaveLabel + "ถูกปฏิเสธ");
        notif.setMessage("อาจารย์ปฏิเสธใบ" + leaveLabel + " วิชา " + lr.getSubjectCode() + " " + lr.getSubjectName()
                + " วันที่ " + lr.getLeaveDate().toString() + " กรุณาติดต่ออาจารย์ผู้สอน");
        notif.setIsRead(false);
        notificationRepository.save(notif);

        return ResponseEntity.ok(Map.of("message", "ปฏิเสธใบลาเรียบร้อยแล้ว"));
    }

    // ==========================================
    // DELETE /api/leave-requests/{id} — ลบใบลา
    // ==========================================
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteLeaveRequest(@PathVariable UUID id) {
        var opt = leaveRequestRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("message", "ไม่พบใบลา"));
        }
        leaveRequestRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "ลบใบลาเรียบร้อยแล้ว"));
    }
}
