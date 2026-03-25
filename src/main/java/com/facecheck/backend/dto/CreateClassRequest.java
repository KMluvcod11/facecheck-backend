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
    private String startTime;   // รับเป็น String "09:00" แล้วแปลงใน Controller
    private String endTime;     // รับเป็น String "12:00"
    private Integer lateThresholdMinutes;
}
