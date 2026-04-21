package com.facecheck.backend.controller;

import com.facecheck.backend.dto.CheckInRequest;
import com.facecheck.backend.entity.Attendance;
import com.facecheck.backend.entity.ClassEntity;
import com.facecheck.backend.entity.User;
import com.facecheck.backend.repository.AttendanceRepository;
import com.facecheck.backend.repository.ClassRepository;
import com.facecheck.backend.repository.UserRepository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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

    private static final double FACE_MATCH_THRESHOLD = 0.45;

    @PostMapping("/check-in")
    public ResponseEntity<?> checkIn(@RequestBody CheckInRequest request) {
        Map<String, Object> response = new HashMap<>();

        try {
            if (request.getStudentId() == null || request.getStudentId().isEmpty()) {
                response.put("message", "ส่งข้อมูลไม่สำเร็จ: ไม่พบรหัสนักศึกษา");
                return ResponseEntity.status(400).body(response);
            }

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

            // 1. ตรวจสอบสถานะเวลาและวันที่ (เพื่อดักทางข้ามวัน)
            ZoneId thaiZone = ZoneId.of("Asia/Bangkok");
            LocalDateTime nowDateTime = LocalDateTime.now(thaiZone);
            LocalTime nowTime = nowDateTime.toLocalTime();
            LocalDate nowDate = nowDateTime.toLocalDate();

            // ✅ 2. ตรวจสอบการเช็กชื่อซ้ำ (ดักคนเช็กสองรอบในวันเดียว)
            LocalDateTime startOfDay = nowDate.atStartOfDay();
            LocalDateTime endOfDay = nowDate.atTime(23, 59, 59);

            List<Attendance> existingAttendance = attendanceRepository.findByClassIdAndCheckedAtBetween(
                    classInfo.getId(), startOfDay, endOfDay
            );

            boolean alreadyCheckedIn = existingAttendance.stream()
                    .anyMatch(a -> a.getStudentId().equals(student.getId()));

            if (alreadyCheckedIn) {
                response.put("message", "ไม่สำเร็จ: คุณได้เช็กชื่อวิชานี้ไปเรียบร้อยแล้วในวันนี้");
                return ResponseEntity.status(409).body(response); // 409 Conflict
            }

            // 3. ตรวจสอบพิกัด GPS
            if (classInfo.getLatitude() != null && classInfo.getLongitude() != null && classInfo.getRadius() != null) {
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

            // 4. เปรียบเทียบใบหน้า
            String savedFaceJson = student.getFaceDescriptor();
            if (savedFaceJson == null || savedFaceJson.isEmpty() || savedFaceJson.equals("[]")) {
                response.put("message", "เช็คชื่อไม่สำเร็จ: คุณยังไม่ได้ลงทะเบียนข้อมูลใบหน้าในระบบ");
                return ResponseEntity.status(400).body(response);
            }

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
            } catch (JsonProcessingException e) {
                logger.error("Error parsing face JSON", e);
                response.put("message", "เกิดข้อผิดพลาดในการอ่านข้อมูลใบหน้าจากระบบ");
                return ResponseEntity.status(500).body(response);
            }

            if (!isFaceMatched) {
                response.put("message", "ใบหน้าไม่ตรงกับที่ลงทะเบียนไว้ กรุณาสแกนใหม่อีกครั้ง");
                return ResponseEntity.status(401).body(response);
            }

            // 5. คำนวณสถานะเวลา
            String status = "on_time";
            if (classInfo.getStartTime() != null) {
                // 5.1 ตรวจสอบตารางเรียน scheduledDates
                String scheduledDatesJson = classInfo.getScheduledDates();
                boolean isClassDay = false;
                if (scheduledDatesJson != null && !scheduledDatesJson.isEmpty()) {
                    try {
                        ObjectMapper mapper = new ObjectMapper();
                        List<Map<String, Object>> scheduledDates = mapper.readValue(scheduledDatesJson, new TypeReference<List<Map<String, Object>>>(){});
                        isClassDay = scheduledDates.stream()
                                .anyMatch(d -> LocalDate.parse(d.get("date").toString()).equals(nowDate));
                    } catch (JsonProcessingException | DateTimeParseException e) {
                        logger.error("Error parsing scheduled dates", e);
                    }
                }

                if (!isClassDay) {
                    response.put("message", "ไม่สามารถเช็คชื่อได้ เนื่องจากวันนี้ไม่มีตารางเรียนที่กำหนดไว้");
                    return ResponseEntity.status(403).body(response);
                }

                Integer lateThreshold = classInfo.getLateThresholdMinutes();
                int lateMin = lateThreshold != null ? lateThreshold : 15;
                LocalTime lateStartTime = classInfo.getStartTime().plusMinutes(lateMin);
                LocalTime absentStartTime = classInfo.getEndTime();

                if (absentStartTime == null) absentStartTime = classInfo.getStartTime().plusHours(2);

                if (nowTime.isBefore(classInfo.getStartTime())) {
                    response.put("message", "ยังไม่ถึงเวลาเริ่มคลาสเรียน");
                    return ResponseEntity.status(403).body(response);
                } else if (nowTime.isAfter(absentStartTime)) {
                    response.put("message", "ไม่สำเร็จ หมดเวลาเช็คชื่อแล้ว (เลยกำหนดเวลาขาดเรียน)");
                    return ResponseEntity.status(403).body(response);
                } else if (nowTime.isAfter(lateStartTime)) {
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
            attendance.setCheckedAt(nowDateTime);

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

    @GetMapping("/class/{classId}")
    public ResponseEntity<?> getClassAttendance(@PathVariable java.util.UUID classId, @RequestParam(required = false) String date) {
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
                item.put("userId", record.getStudentId().toString());
                Optional<User> userOpt = userRepository.findById(record.getStudentId());
                item.put("studentId", userOpt.isPresent() ? userOpt.get().getStudentId() : null);
                String status = record.getStatus();
                if ("on_time".equals(status)) status = "PRESENT";
                else if (status != null) status = status.toUpperCase();
                item.put("status", status);
                item.put("checkedAt", record.getCheckedAt() != null ? record.getCheckedAt().toString() : null);
                result.add(item);
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("message", "ดึงข้อมูลการเช็คชื่อไม่สำเร็จ: " + e.getMessage()));
        }
    }

    @GetMapping("/class/{classId}/daily")
    public ResponseEntity<?> getDailyAttendance(@PathVariable java.util.UUID classId, @RequestParam String date) {
        try {
            java.time.LocalDate localDate = java.time.LocalDate.parse(date);
            LocalDateTime startOfDay = localDate.atStartOfDay();
            LocalDateTime endOfDay = localDate.atTime(23, 59, 59);
            List<Attendance> records = attendanceRepository.findByClassIdAndCheckedAtBetween(classId, startOfDay, endOfDay);
            List<Map<String, Object>> result = new java.util.ArrayList<>();
            for (Attendance record : records) {
                Map<String, Object> item = new HashMap<>();
                item.put("studentId", record.getStudentId().toString());
                Optional<User> userOpt = userRepository.findById(record.getStudentId());
                if (userOpt.isPresent()) {
                    item.put("studentCode", userOpt.get().getStudentId());
                    item.put("studentName", userOpt.get().getFullName());
                }
                String status = record.getStatus();
                if ("on_time".equals(status)) status = "present";
                item.put("status", status);
                if (record.getCheckedAt() != null) {
                    item.put("time", record.getCheckedAt().toLocalTime().toString().substring(0, 5));
                }
                result.add(item);
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("message", "ดึงข้อมูลสถิติรายวันไม่สำเร็จ: " + e.getMessage()));
        }
    }

    @GetMapping("/student/{studentId}")
    public ResponseEntity<?> getStudentAttendanceHistory(@PathVariable java.util.UUID studentId) {
        try {
            List<Attendance> records = attendanceRepository.findByStudentIdOrderByCheckedAtDesc(studentId);
            List<Map<String, Object>> result = new ArrayList<>();

            // --- 1. เพิ่ม records ที่มีจริงเข้าไปก่อน (present / late) ---
            // รวบรวม classIds ที่นักศึกษาลงทะเบียนอยู่จาก records ที่มี
            Set<java.util.UUID> enrolledClassIds = new HashSet<>();
            Map<java.util.UUID, Map<LocalDate, Boolean>> attendedDatesPerClass = new HashMap<>();

            for (Attendance a : records) {
                Map<String, Object> data = new HashMap<>();
                data.put("id", a.getId());
                String status = a.getStatus();
                if ("on_time".equals(status)) status = "present";
                data.put("status", status);
                data.put("checkedAt", a.getCheckedAt());
                data.put("classId", a.getClassId());
                var classOpt = classRepository.findById(a.getClassId());
                if (classOpt.isPresent()) {
                    data.put("subjectCode", classOpt.get().getSubjectCode());
                    data.put("subjectName", classOpt.get().getSubjectName());
                } else {
                    data.put("subjectCode", "N/A");
                    data.put("subjectName", "ไม่ทราบชื่อวิชา");
                }
                result.add(data);

                // Track attended dates per class
                enrolledClassIds.add(a.getClassId());
                attendedDatesPerClass
                    .computeIfAbsent(a.getClassId(), k -> new HashMap<>())
                    .put(a.getCheckedAt().toLocalDate(), true);
            }

            // --- 2. หา absent records จาก scheduledDates ที่ผ่านมาแล้ว ---
            LocalDate today = LocalDate.now(ZoneId.of("Asia/Bangkok"));
            ObjectMapper mapper = new ObjectMapper();

            for (java.util.UUID classId : enrolledClassIds) {
                var classOpt = classRepository.findById(classId);
                if (classOpt.isEmpty()) continue;

                var classEntity = classOpt.get();
                String scheduledJson = classEntity.getScheduledDates();
                if (scheduledJson == null || scheduledJson.isEmpty()) continue;

                try {
                    List<Map<String, Object>> scheduledDates = mapper.readValue(
                        scheduledJson, new TypeReference<List<Map<String, Object>>>(){});

                    Map<LocalDate, Boolean> attendedDates = attendedDatesPerClass.getOrDefault(classId, new HashMap<>());

                    for (Map<String, Object> d : scheduledDates) {
                        LocalDate classDate = LocalDate.parse(d.get("date").toString());
                        // นับเฉพาะวันที่ผ่านมาแล้วและไม่มี record เช็กชื่อ
                        if (!classDate.isAfter(today) && !attendedDates.containsKey(classDate)) {
                            Map<String, Object> absentRecord = new HashMap<>();
                            absentRecord.put("id", "absent-" + classId + "-" + classDate);
                            absentRecord.put("status", "absent");
                            absentRecord.put("checkedAt", classDate.atTime(classEntity.getStartTime() != null ? classEntity.getStartTime() : LocalTime.of(0,0)));
                            absentRecord.put("classId", classId);
                            absentRecord.put("subjectCode", classEntity.getSubjectCode());
                            absentRecord.put("subjectName", classEntity.getSubjectName());
                            result.add(absentRecord);
                        }
                    }
                } catch (JsonProcessingException | DateTimeParseException ex) {
                    logger.error("Error parsing scheduledDates for class " + classId, ex);
                }
            }

            // --- 3. เรียงลำดับจากใหม่ไปเก่า ---
            result.sort((a, b) -> {
                Object aDate = a.get("checkedAt");
                Object bDate = b.get("checkedAt");
                if (aDate == null && bDate == null) return 0;
                if (aDate == null) return 1;
                if (bDate == null) return -1;
                return bDate.toString().compareTo(aDate.toString());
            });

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("message", "ดึงข้อมูลประวัติไม่สำเร็จ: " + e.getMessage()));
        }
    }

    @GetMapping("/student/{studentId}/stats/{classId}")
    public ResponseEntity<Map<String, Object>> getStudentStats(@PathVariable java.util.UUID studentId, @PathVariable java.util.UUID classId) {
        List<Attendance> studentAttendances = attendanceRepository.findByStudentIdAndClassId(studentId, classId);
        int present = 0;
        int late = 0;
        Set<LocalDate> attendedDates = new HashSet<>();

        for (Attendance a : studentAttendances) {
            String status = a.getStatus() != null ? a.getStatus().trim().toLowerCase() : "";
            if (status.equals("present") || status.equals("on_time")) present++;
            else if (status.equals("late")) late++;
            if (a.getCheckedAt() != null) {
                attendedDates.add(a.getCheckedAt().toLocalDate());
            }
        }

        // คำนวณ absent จาก scheduledDates ที่ผ่านมาแล้ว
        int absent = 0;
        Optional<ClassEntity> classOpt = classRepository.findById(classId);
        if (classOpt.isPresent()) {
            String scheduledJson = classOpt.get().getScheduledDates();
            if (scheduledJson != null && !scheduledJson.isEmpty()) {
                try {
                    ObjectMapper mapper = new ObjectMapper();
                    List<Map<String, Object>> scheduledDates = mapper.readValue(
                        scheduledJson, new TypeReference<List<Map<String, Object>>>(){});
                    LocalDate today = LocalDate.now(ZoneId.of("Asia/Bangkok"));
                    for (Map<String, Object> d : scheduledDates) {
                        LocalDate classDate = LocalDate.parse(d.get("date").toString());
                        // วันที่ผ่านมาแล้วและไม่มี record = ขาดเรียน
                        if (!classDate.isAfter(today) && !attendedDates.contains(classDate)) {
                            absent++;
                        }
                    }
                } catch (JsonProcessingException | DateTimeParseException e) {
                    logger.error("Error calculating absent count", e);
                }
            }
        }

        Map<String, Object> stats = new HashMap<>();
        stats.put("present", present);
        stats.put("late", late);
        stats.put("absent", absent);
        return ResponseEntity.ok(stats);
    }
}