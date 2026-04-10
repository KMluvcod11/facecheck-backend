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
    private String term;            // ปีการศึกษา / เทอม
    private String instructorName;  // ชื่ออาจารย์ผู้สอน
    private String scheduledDates;  // JSON String ของวันที่เช็คชื่อ
    
    // พิกัด GPS
    private Double latitude;
    private Double longitude;
    private Double radius;
}
