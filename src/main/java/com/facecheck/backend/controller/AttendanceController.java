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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {

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

            // 3. ตรวจสอบพิกัด GPS
            if (classInfo.getLatitude() != null && classInfo.getLongitude() != null && classInfo.getRadius() != null) {
                double distance = calculateGPSDistance(
                        classInfo.getLatitude(), classInfo.getLongitude(),
                        request.getLatitude(), request.getLongitude()
                );

                if (distance > classInfo.getRadius()) {
                    response.put("message", "คุณอยู่นอกพื้นที่เช็กชื่อ (" + String.format("%.2f", distance) + " เมตร)");
                    return ResponseEntity.status(403).body(response);
                }
            }

            // 4. เปรียบเทียบใบหน้า (Face Matching)
            boolean isFaceMatched = false;
            String savedFaceJson = student.getFaceDescriptor();

            if (savedFaceJson != null && !savedFaceJson.isEmpty() && request.getFaceDescriptor() != null) {
                ObjectMapper mapper = new ObjectMapper();
                List<List<Double>> savedDescriptors = mapper.readValue(savedFaceJson, new TypeReference<List<List<Double>>>(){});

                for (List<Double> savedDesc : savedDescriptors) {
                    double faceDistance = calculateEuclideanDistance(request.getFaceDescriptor(), savedDesc);
                    if (faceDistance <= FACE_MATCH_THRESHOLD) {
                        isFaceMatched = true;
                        break;
                    }
                }
            }

            if (!isFaceMatched) {
                response.put("message", "ใบหน้าไม่ถูกต้อง กรุณาสแกนใหม่อีกครั้ง");
                return ResponseEntity.status(401).body(response);
            }

            // 5. คำนวณสถานะ (แก้ไขให้ตรงกับ Database Constraint)
            LocalTime nowTime = LocalTime.now();
            String status = "on_time"; // ✅ เปลี่ยนจาก present เป็น on_time

            if (classInfo.getStartTime() != null) {
                // สาย: เกินเวลาเริ่ม + threshold (เช่น 15 นาที)
                LocalTime lateTime = classInfo.getStartTime().plusMinutes(classInfo.getLateThresholdMinutes());
                // ขาด: เกินเวลาเริ่ม + (threshold * 2) หรือตามที่กำหนด
                LocalTime absentTime = classInfo.getStartTime().plusMinutes(classInfo.getLateThresholdMinutes() * 2);

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
            attendance.setCheckedAt(LocalDateTime.now());

            attendanceRepository.save(attendance);

            response.put("message", "เช็กชื่อสำเร็จ!");
            response.put("status", status);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            response.put("message", "เกิดข้อผิดพลาด: " + e.getMessage());
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
}