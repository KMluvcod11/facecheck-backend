package com.facecheck.backend.controller;

import com.facecheck.backend.entity.*;
import com.facecheck.backend.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
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

    @Autowired
    private AttendanceRepository attendanceRepository;

    // ==========================================
    // POST /api/leave-requests — นักศึกษาส่งใบลา
    // ==========================================
    @PostMapping
    public ResponseEntity<?> createLeaveRequest(@RequestBody Map<String, Object> payload) {
        try {
            UUID studentId = UUID.fromString(payload.get("studentId").toString());
            UUID classId = UUID.fromString(payload.get("classId").toString());
            String leaveType = payload.get("leaveType").toString();
            String leaveDate = payload.get("leaveDate").toString();
            String reason = payload.getOrDefault("reason", "").toString();
            String attachmentImage = payload.containsKey("attachmentImage") ? payload.get("attachmentImage").toString() : null;

            var classOpt = classRepository.findById(classId);
            if (classOpt.isEmpty()) {
                return errorResponse(404, "ไม่พบคลาสนี้");
            }
            ClassEntity classEntity = classOpt.get();

            var userOpt = userRepository.findById(studentId);
            if (userOpt.isEmpty()) {
                return errorResponse(404, "ไม่พบนักศึกษา");
            }
            User student = userOpt.get();

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

            Map<String, Object> response = new HashMap<>();
            response.put("message", "ส่งใบลาเรียบร้อยแล้ว");
            response.put("leaveRequest", lr);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return errorResponse(400, "ส่งใบลาไม่สำเร็จ: " + e.getMessage());
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
        try {
            var opt = leaveRequestRepository.findById(id);
            if (opt.isEmpty()) {
                return errorResponse(404, "ไม่พบใบลา");
            }

            LeaveRequest lr = opt.get();
            lr.setStatus("approved");
            lr.setUpdatedAt(LocalDateTime.now());
            leaveRequestRepository.save(lr);

            // บันทึกสถานะ "leave" ลงใน Attendance
            Attendance attendance = findOrCreateAttendance(lr);
            attendance.setStatus("leave");
            attendanceRepository.save(attendance);

            // ส่ง Notification แจ้งนักศึกษา
            String leaveLabel = lr.getLeaveType().equals("sick") ? "ลาป่วย" : "ลากิจ";
            sendNotification(lr.getStudentId(), lr.getClassId(), "info",
                    "✅ ใบ" + leaveLabel + "ได้รับการอนุมัติแล้ว",
                    "อาจารย์อนุมัติใบ" + leaveLabel + " วิชา " + lr.getSubjectCode() + " " + lr.getSubjectName()
                            + " วันที่ " + lr.getLeaveDate().toString() + " เรียบร้อยแล้ว");

            return successResponse("อนุมัติใบลาเรียบร้อยแล้ว");
        } catch (Exception e) {
            return errorResponse(500, "เกิดข้อผิดพลาด: " + e.getMessage());
        }
    }

    // ==========================================
    // PUT /api/leave-requests/{id}/reject — ปฏิเสธใบลา
    // ==========================================
    @PutMapping("/{id}/reject")
    public ResponseEntity<?> rejectLeaveRequest(@PathVariable UUID id) {
        try {
            var opt = leaveRequestRepository.findById(id);
            if (opt.isEmpty()) {
                return errorResponse(404, "ไม่พบใบลา");
            }

            LeaveRequest lr = opt.get();
            lr.setStatus("rejected");
            lr.setUpdatedAt(LocalDateTime.now());
            leaveRequestRepository.save(lr);

            // บันทึกสถานะ "absent" ลงใน Attendance เพื่อให้แสดงว่า "ขาดเรียน"
            Attendance attendance = findOrCreateAttendance(lr);
            attendance.setStatus("absent");
            attendanceRepository.save(attendance);

            // ส่ง Notification แจ้งนักศึกษา
            String leaveLabel = lr.getLeaveType().equals("sick") ? "ลาป่วย" : "ลากิจ";
            sendNotification(lr.getStudentId(), lr.getClassId(), "danger",
                    "❌ ใบ" + leaveLabel + "ถูกปฏิเสธ",
                    "อาจารย์ปฏิเสธใบ" + leaveLabel + " วิชา " + lr.getSubjectCode() + " " + lr.getSubjectName()
                            + " วันที่ " + lr.getLeaveDate().toString() + " กรุณาติดต่ออาจารย์ผู้สอน");

            return successResponse("ปฏิเสธใบลาเรียบร้อยแล้ว");
        } catch (Exception e) {
            return errorResponse(500, "เกิดข้อผิดพลาด: " + e.getMessage());
        }
    }

    // ==========================================
    // DELETE /api/leave-requests/{id} — ลบใบลา
    // ==========================================
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteLeaveRequest(@PathVariable UUID id) {
        try {
            var opt = leaveRequestRepository.findById(id);
            if (opt.isEmpty()) {
                return errorResponse(404, "ไม่พบใบลา");
            }
            leaveRequestRepository.deleteById(id);
            return successResponse("ลบใบลาเรียบร้อยแล้ว");
        } catch (Exception e) {
            return errorResponse(500, "เกิดข้อผิดพลาด: " + e.getMessage());
        }
    }

    // ==========================================
    // Helper Methods
    // ==========================================

    /** ค้นหา Attendance record ที่มีอยู่แล้ว หรือสร้างใหม่พร้อมตั้งค่าเริ่มต้น */
    private Attendance findOrCreateAttendance(LeaveRequest lr) {
        LocalDateTime startOfDay = lr.getLeaveDate().atStartOfDay();
        LocalDateTime endOfDay = lr.getLeaveDate().atTime(23, 59, 59);
        List<Attendance> existing = attendanceRepository.findByClassIdAndCheckedAtBetween(lr.getClassId(), startOfDay, endOfDay);

        Attendance attendance = existing.stream()
                .filter(a -> a.getStudentId().equals(lr.getStudentId()))
                .findFirst()
                .orElse(new Attendance());

        attendance.setClassId(lr.getClassId());
        attendance.setStudentId(lr.getStudentId());
        if (attendance.getCheckedAt() == null) {
            attendance.setCheckedAt(lr.getLeaveDate().atTime(8, 0, 0));
        }
        return attendance;
    }

    /** ส่ง Notification ให้นักศึกษา */
    private void sendNotification(UUID userId, UUID classId, String type, String title, String message) {
        Notification notif = new Notification();
        notif.setUserId(userId);
        notif.setClassId(classId);
        notif.setType(type);
        notif.setTitle(title);
        notif.setMessage(message);
        notif.setIsRead(false);
        notificationRepository.save(notif);
    }

    /** สร้าง error response */
    private ResponseEntity<?> errorResponse(int status, String message) {
        Map<String, String> response = new HashMap<>();
        response.put("message", message);
        return ResponseEntity.status(status).body(response);
    }

    /** สร้าง success response */
    private ResponseEntity<?> successResponse(String message) {
        Map<String, String> response = new HashMap<>();
        response.put("message", message);
        return ResponseEntity.ok(response);
    }
}
