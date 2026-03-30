package com.facecheck.backend.dto;

import java.util.List;
import java.util.UUID;

public class CheckInRequest {
    private UUID classId;
    private String studentId; // รหัสนักศึกษา (สตริง)
    private Double latitude;
    private Double longitude;
    private List<Double> faceDescriptor; // ข้อมูลใบหน้าตอนสแกนเช็คชื่อ (ชุดเดียว)

    // --- Getters & Setters ---
    public UUID getClassId() { return classId; }
    public void setClassId(UUID classId) { this.classId = classId; } // ✅ แก้ตรงนี้ (เติม . ระหว่าง this กับ classId)
    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
    public List<Double> getFaceDescriptor() { return faceDescriptor; }
    public void setFaceDescriptor(List<Double> faceDescriptor) { this.faceDescriptor = faceDescriptor; }
}