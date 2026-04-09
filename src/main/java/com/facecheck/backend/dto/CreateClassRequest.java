package com.facecheck.backend.dto;

import lombok.Data;
import java.util.UUID;

@Data
public class CreateClassRequest {
    private UUID teacherId;
    private String subjectName;
    private String subjectCode;
    private String room;
    private String scheduleDay;
    private String startTime;
    private String endTime;
    private Integer lateThresholdMinutes;
    private String term;
    private String instructorName;
    private String scheduledDatesJson;

    // ✅ เพิ่ม 3 บรรทัดนี้สำหรับรับค่าพิกัด GPS
    private Double latitude;
    private Double longitude;
    // ✅ ของใหม่ที่ถูกต้อง
    private Double radius;
}