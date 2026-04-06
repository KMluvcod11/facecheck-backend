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
import java.util.*;

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
            // ✅ 1. เพิ่มโค้ดดักจับตรงนี้: ถ้า React ส่งค่าว่างมา ให้เตือนกลับไปเลย ไม่ต้องไปค้นหาใน Database
            if (request.getStudentId() == null || request.getStudentId().isEmpty()) {
                response.put("message", "ส่งข้อมูลไม่สำเร็จ: ไม่พบรหัสนักศึกษา (React ส่งค่า Null มา)");
                return ResponseEntity.status(400).body(response);
            }

            // 2. ดึงข้อมูลคลาส
            Optional<ClassEntity> classOpt = classRepository.findById(request.getClassId());
            Optional<User> userOpt;

            try {
                userOpt = userRepository.findByStudentId(request.getStudentId());
            } catch (Exception e) {
                response.put("message", "Error: พบรหัสนักศึกษา [" + request.getStudentId() + "] ซ้ำกัน!");
                return ResponseEntity.status(500).body(response);
            }

            // ✅ เปลี่ยนตรงนี้ เพื่อให้มันบอกชัดเจนว่าอะไรหายไป
            if (classOpt.isEmpty()) {
                response.put("message", "ระบบขัดข้อง: ไม่พบคลาสเรียน ID [" + request.getClassId() + "] ในฐานข้อมูล");
                return ResponseEntity.status(404).body(response);
            }
            if (userOpt.isEmpty()) {
                response.put("message", "ระบบขัดข้อง: ไม่พบนักศึกษารหัส [" + request.getStudentId() + "] ในฐานข้อมูล (คุณอาจจะเผลอลบไปแล้ว)");
                return ResponseEntity.status(404).body(response);
            }

            // ... (โค้ดด้านล่างเหมือนเดิม)
            ClassEntity classInfo = classOpt.get();
            User student = userOpt.get();

            // 2. ตรวจสอบพิกัด GPS (ถ้าอาจารย์มีการตั้งค่าพิกัดไว้)
            if (classInfo.getLatitude() != null && classInfo.getLongitude() != null && classInfo.getRadius() != null) {
                double distance = calculateGPSDistance(
                        classInfo.getLatitude(), classInfo.getLongitude(),
                        request.getLatitude(), request.getLongitude()
                );

                if (distance > classInfo.getRadius()) {
                    response.put("message", "พิกัดของคุณอยู่นอกพื้นที่ห้องเรียน (" + String.format("%.2f", distance) + " เมตร)");
                    return ResponseEntity.status(403).body(response);
                }
            }

            // 3. เปรียบเทียบใบหน้า (Face Matching)
            boolean isFaceMatched = false;
            String savedFaceJson = student.getFaceDescriptor();

            if (savedFaceJson != null && !savedFaceJson.isEmpty() && request.getFaceDescriptor() != null) {
                ObjectMapper mapper = new ObjectMapper();
                // แปลง JSON ที่เก็บไว้ (4 มุม) กลับมาเป็น List
                List<List<Double>> savedDescriptors = mapper.readValue(savedFaceJson, new TypeReference<List<List<Double>>>(){});

                // เทียบกับที่ส่งมาทีละมุม ถ้าผ่านมุมใดมุมหนึ่งถือว่าใช่
                for (List<Double> savedDesc : savedDescriptors) {
                    double faceDistance = calculateEuclideanDistance(request.getFaceDescriptor(), savedDesc);
                    if (faceDistance <= FACE_MATCH_THRESHOLD) {
                        isFaceMatched = true;
                        break;
                    }
                }
            }

            if (!isFaceMatched) {
                response.put("message", "ใบหน้าไม่ตรงกับที่ลงทะเบียนไว้ กรุณาลองใหม่");
                return ResponseEntity.status(401).body(response);
            }

            // 4. คำนวณสถานะ (ตรงเวลา / สาย / ขาด)
            LocalTime nowTime = LocalTime.now();
            String status = "present";

            if (classInfo.getStartTime() != null) {
                LocalTime lateTime = classInfo.getStartTime().plusMinutes(classInfo.getLateThresholdMinutes());
                LocalTime absentTime = classInfo.getStartTime().plusMinutes(classInfo.getLateThresholdMinutes() * 2); // สมมติขาดเรียนคือสายเกิน 2 เท่า

                if (nowTime.isAfter(absentTime)) {
                    status = "absent";
                } else if (nowTime.isAfter(lateTime)) {
                    status = "late";
                }
            }

            // 5. บันทึกลงตาราง Attendance
            Attendance attendance = new Attendance();
            attendance.setClassId(classInfo.getId());
            attendance.setStudentId(student.getId());
            attendance.setStatus(status);
            attendance.setLatitude(request.getLatitude());
            attendance.setLongitude(request.getLongitude());
            attendance.setCheckedAt(LocalDateTime.now());

            attendanceRepository.save(attendance);

            response.put("message", "เช็คชื่อสำเร็จ!");
            response.put("status", status);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            response.put("message", "เกิดข้อผิดพลาดภายในระบบ: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    // ================= HELPER METHODS =================

    // คำนวณระยะห่างใบหน้า (Euclidean Distance)
    private double calculateEuclideanDistance(List<Double> desc1, List<Double> desc2) {
        if (desc1.size() != desc2.size()) return 999.0;
        double sum = 0.0;
        for (int i = 0; i < desc1.size(); i++) {
            double diff = desc1.get(i) - desc2.get(i);
            sum += diff * diff;
        }
        return Math.sqrt(sum);
    }

    // คำนวณระยะห่าง GPS (Haversine Formula) คืนค่าเป็นเมตร
    private double calculateGPSDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371000; // รัศมีโลก (เมตร)
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
    // ==========================================
    // GET /api/attendance/student/{studentId} — ดึงประวัติการเข้าเรียนของนักศึกษา
    // ==========================================
    @GetMapping("/student/{studentId}")
    public ResponseEntity<?> getStudentAttendanceHistory(@PathVariable UUID studentId) {
        try {
            // 1. ดึงประวัติทั้งหมดของนักศึกษาคนนี้ (เรียงจากใหม่ไปเก่า)
            List<com.facecheck.backend.entity.Attendance> records = attendanceRepository.findByStudentIdOrderByCheckedAtDesc(studentId);

            List<Map<String, Object>> result = new ArrayList<>();
            for (com.facecheck.backend.entity.Attendance a : records) {
                Map<String, Object> data = new HashMap<>();
                data.put("id", a.getId());
                data.put("status", a.getStatus());
                data.put("checkedAt", a.getCheckedAt()); // เวลาที่เช็คชื่อ

                // 2. ดึงข้อมูลวิชาเพื่อเอารหัสและชื่อวิชามาแสดงด้วย
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