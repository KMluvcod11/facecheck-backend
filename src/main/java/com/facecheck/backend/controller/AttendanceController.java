package com.facecheck.backend.controller;

import com.facecheck.backend.dto.CheckInRequest;
import com.facecheck.backend.entity.Attendance;
import com.facecheck.backend.entity.ClassEntity;
import com.facecheck.backend.entity.User;
import com.facecheck.backend.repository.AttendanceRepository;
import com.facecheck.backend.repository.ClassRepository;
import com.facecheck.backend.repository.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {

    private static final Logger logger = LoggerFactory.getLogger(AttendanceController.class);

    @Autowired
    private AttendanceRepository attendanceRepository;
    @Autowired
    private ClassRepository classRepository;
    @Autowired
    private UserRepository userRepository;

    // ระยะห่างใบหน้าที่ยอมรับได้ (ยิ่งน้อยยิ่งเข้มงวด ปกติใช้ 0.4 - 0.5)
    private static final double FACE_MATCH_THRESHOLD = 0.45;

    @PostMapping("/check-in")
    public ResponseEntity<?> checkIn(@RequestBody CheckInRequest request) {
        Map<String, Object> response = new HashMap<>();

        try {
            // 1. ตรวจสอบข้อมูลเบื้องต้น
            if (request.getStudentId() == null || request.getStudentId().isEmpty()) {
                response.put("message", "ส่งข้อมูลไม่สำเร็จ: ไม่พบรหัสนักศึกษา");
                return ResponseEntity.status(400).body(response);
            }

            // 2. ดึงข้อมูลคลาสและนักศึกษา
            Optional<ClassEntity> classOpt = classRepository.findById(request.getClassId());
            Optional<User> userOpt = userRepository.findByStudentId(request.getStudentId());

            if (classOpt.isEmpty()) {
                response.put("message", "ไม่พบคลาสเรียนในระบบ");
                return ResponseEntity.status(404).body(response);
            }
            if (userOpt.isEmpty()) {
                response.put("message", "ไม่พบข้อมูลนักศึกษาในระบบ");
                return ResponseEntity.status(404).body(response);
            }

            ClassEntity classInfo = classOpt.get();
            User student = userOpt.get();

            // 3. ตรวจสอบพิกัด GPS (ถ้าอาจารย์มีการตั้งพิกัดไว้)
            if (classInfo.getLatitude() != null && classInfo.getLongitude() != null && classInfo.getRadius() != null) {

                // ✅ ดักจับกรณีนักศึกษาไม่ได้เปิด GPS ในมือถือ
                if (request.getLatitude() == null || request.getLongitude() == null) {
                    response.put("message", "ไม่สามารถเช็คชื่อได้: กรุณาอนุญาตให้ระบบเข้าถึงตำแหน่งที่ตั้ง (GPS)");
                    return ResponseEntity.status(400).body(response);
                }

                double distance = calculateGPSDistance(
                        classInfo.getLatitude(), classInfo.getLongitude(),
                        request.getLatitude(), request.getLongitude()
                );

                if (distance > classInfo.getRadius()) {
                    response.put("message", "คุณอยู่นอกพื้นที่เช็กชื่อ (" + String.format("%.2f", distance) + " เมตร / อนุญาต " + classInfo.getRadius() + " เมตร)");
                    return ResponseEntity.status(403).body(response);
                }
            }

            // 4. เปรียบเทียบใบหน้า (Face Matching)
            String savedFaceJson = student.getFaceDescriptor();

            // ✅ ดักจับกรณีที่นักศึกษายังไม่ได้อัปโหลดใบหน้าลงระบบเลย
            if (savedFaceJson == null || savedFaceJson.isEmpty() || savedFaceJson.equals("[]")) {
                response.put("message", "เช็คชื่อไม่สำเร็จ: คุณยังไม่ได้ลงทะเบียนข้อมูลใบหน้าในระบบ");
                return ResponseEntity.status(400).body(response);
            }

            // ✅ ดักจับกรณีสแกนหน้าไม่ติดแล้ว React ส่งค่าว่างมา
            if (request.getFaceDescriptor() == null || request.getFaceDescriptor().isEmpty()) {
                response.put("message", "ระบบไม่ได้รับข้อมูลใบหน้าจากการสแกน กรุณาลองใหม่อีกครั้ง");
                return ResponseEntity.status(400).body(response);
            }

            boolean isFaceMatched = false;
            try {
                ObjectMapper mapper = new ObjectMapper();
                List<List<Double>> savedDescriptors = mapper.readValue(savedFaceJson, new TypeReference<List<List<Double>>>(){});

                for (List<Double> savedDesc : savedDescriptors) {
                    double faceDistance = calculateEuclideanDistance(request.getFaceDescriptor(), savedDesc);
                    if (faceDistance <= FACE_MATCH_THRESHOLD) {
                        isFaceMatched = true;
                        break;
                    }
                }
            } catch (Exception e) {
                logger.error("Error parsing face JSON", e);
                response.put("message", "เกิดข้อผิดพลาดในการอ่านข้อมูลใบหน้าจากระบบ");
                return ResponseEntity.status(500).body(response);
            }

            if (!isFaceMatched) {
                response.put("message", "ใบหน้าไม่ตรงกับที่ลงทะเบียนไว้ กรุณาสแกนใหม่อีกครั้ง");
                return ResponseEntity.status(401).body(response);
            }

            // 5. คำนวณสถานะเวลา (บังคับใช้เวลาไทยเพื่อป้องกันการคำนวณผิดพลาด)
            java.time.ZoneId thaiZone = java.time.ZoneId.of("Asia/Bangkok");
            LocalTime nowTime = LocalTime.now(thaiZone);
            String status = "on_time";

            if (classInfo.getStartTime() != null) {
                int lateMin = classInfo.getLateThresholdMinutes() != null ? classInfo.getLateThresholdMinutes() : 15;
                LocalTime lateTime = classInfo.getStartTime().plusMinutes(lateMin);
                LocalTime absentTime = classInfo.getStartTime().plusMinutes(lateMin * 2);

                if (nowTime.isAfter(absentTime)) {
                    status = "absent";
                } else if (nowTime.isAfter(lateTime)) {
                    status = "late";
                }
            }

            // 6. บันทึกลงตาราง Attendance
            Attendance attendance = new Attendance();
            attendance.setClassId(classInfo.getId());
            attendance.setStudentId(student.getId());
            attendance.setStatus(status);
            attendance.setLatitude(request.getLatitude());
            attendance.setLongitude(request.getLongitude());

            // ✅ บันทึกเวลาเช็คชื่อเป็นเวลาไทยลง Database ด้วย
            attendance.setCheckedAt(LocalDateTime.now(thaiZone));

            attendanceRepository.save(attendance);

            response.put("message", "เช็กชื่อสำเร็จ!");
            response.put("status", status);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("เช็กชื่อไม่สำเร็จ", e);
            response.put("message", "เกิดข้อผิดพลาดภายในระบบ: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    // --- Helper Methods ---

    private double calculateEuclideanDistance(List<Double> desc1, List<Double> desc2) {
        if (desc1.size() != desc2.size()) return 999.0;
        double sum = 0.0;
        for (int i = 0; i < desc1.size(); i++) {
            double diff = desc1.get(i) - desc2.get(i);
            sum += diff * diff;
        }
        return Math.sqrt(sum);
    }

    private double calculateGPSDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371000;
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    // ==========================================
    // GET /api/attendance/class/{classId}?date=YYYY-MM-DD — สถิติรายวัน (อาจารย์)
    // ถ้าไม่ส่ง date → return ทั้งหมดของคลาสนั้น
    // ถ้าส่ง date → filter เฉพาะวันนั้น
    // ==========================================
    @GetMapping("/class/{classId}")
    public ResponseEntity<?> getClassAttendance(
            @PathVariable java.util.UUID classId,
            @RequestParam(required = false) String date) {
        try {
            List<Attendance> records;

            if (date != null && !date.isEmpty()) {
                java.time.LocalDate localDate = java.time.LocalDate.parse(date);
                LocalDateTime startOfDay = localDate.atStartOfDay();
                LocalDateTime endOfDay = localDate.atTime(23, 59, 59);
                records = attendanceRepository.findByClassIdAndCheckedAtBetween(classId, startOfDay, endOfDay);
            } else {
                records = attendanceRepository.findByClassId(classId);
            }

            List<Map<String, Object>> result = new java.util.ArrayList<>();
            for (Attendance record : records) {
                Map<String, Object> item = new HashMap<>();

                // userId = UUID ของ user (ตรงกับ studentId ใน attendance)
                item.put("userId", record.getStudentId().toString());

                // ดึงรหัสนักศึกษา (เช่น 640001) จาก User
                Optional<User> userOpt = userRepository.findById(record.getStudentId());
                if (userOpt.isPresent()) {
                    item.put("studentId", userOpt.get().getStudentId());
                } else {
                    item.put("studentId", null);
                }

                // แปลง status เป็น uppercase: on_time → PRESENT, late → LATE, absent → ABSENT
                String status = record.getStatus();
                if ("on_time".equals(status)) {
                    status = "PRESENT";
                } else if (status != null) {
                    status = status.toUpperCase();
                }
                item.put("status", status);

                // checkedAt เป็น ISO datetime
                item.put("checkedAt", record.getCheckedAt() != null ? record.getCheckedAt().toString() : null);

                result.add(item);
            }

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("message", "ดึงข้อมูลการเช็คชื่อไม่สำเร็จ: " + e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    // ==========================================
    // GET /api/attendance/class/{classId}/daily — สถิติรายวัน
    // ==========================================
    @GetMapping("/class/{classId}/daily")
    public ResponseEntity<?> getDailyAttendance(
            @PathVariable java.util.UUID classId,
            @RequestParam String date) {
        try {
            java.time.LocalDate localDate = java.time.LocalDate.parse(date);
            LocalDateTime startOfDay = localDate.atStartOfDay();
            LocalDateTime endOfDay = localDate.atTime(23, 59, 59);

            List<Attendance> records = attendanceRepository.findByClassIdAndCheckedAtBetween(classId, startOfDay, endOfDay);

            // แปลงข้อมูลให้ Frontend ใช้ได้ง่าย
            List<Map<String, Object>> result = new java.util.ArrayList<>();
            for (Attendance record : records) {
                Map<String, Object> item = new HashMap<>();
                item.put("studentId", record.getStudentId().toString()); // UUID ของ user

                // ดึงข้อมูล studentId (13 หลัก) และชื่อจาก User
                Optional<User> userOpt = userRepository.findById(record.getStudentId());
                if (userOpt.isPresent()) {
                    item.put("studentCode", userOpt.get().getStudentId()); // รหัส 13 หลัก
                    item.put("studentName", userOpt.get().getFullName());
                }

                // แปลง status: on_time → present เพื่อให้ Frontend แสดงผลถูก
                String status = record.getStatus();
                if ("on_time".equals(status)) {
                    status = "present";
                }
                item.put("status", status);

                // เวลาเช็คชื่อ
                if (record.getCheckedAt() != null) {
                    item.put("time", record.getCheckedAt().toLocalTime().toString().substring(0, 5));
                }

                result.add(item);
            }

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("message", "ดึงข้อมูลสถิติรายวันไม่สำเร็จ: " + e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }
    // ==========================================
    // GET /api/attendance/student/{studentId} — ดึงประวัติการเข้าเรียนของนักศึกษา
    // ==========================================
    @GetMapping("/student/{studentId}")
    public ResponseEntity<?> getStudentAttendanceHistory(@PathVariable java.util.UUID studentId) {
        try {
            // ดึงประวัติทั้งหมดของนักศึกษาคนนี้
            List<Attendance> records = attendanceRepository.findByStudentIdOrderByCheckedAtDesc(studentId);

            List<Map<String, Object>> result = new java.util.ArrayList<>();
            for (Attendance a : records) {
                Map<String, Object> data = new HashMap<>();
                data.put("id", a.getId());

                // แปลง status ให้เป็น present/late/absent เพื่อแสดงในหน้า React
                String status = a.getStatus();
                if ("on_time".equals(status)) status = "present";
                data.put("status", status);

                data.put("checkedAt", a.getCheckedAt());

                // ดึงชื่อวิชามาแสดง
                var classOpt = classRepository.findById(a.getClassId());
                if (classOpt.isPresent()) {
                    data.put("subjectCode", classOpt.get().getSubjectCode());
                    data.put("subjectName", classOpt.get().getSubjectName());
                } else {
                    data.put("subjectCode", "N/A");
                    data.put("subjectName", "ไม่ทราบชื่อวิชา");
                }
                result.add(data);
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("message", "ดึงข้อมูลประวัติไม่สำเร็จ: " + e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

}