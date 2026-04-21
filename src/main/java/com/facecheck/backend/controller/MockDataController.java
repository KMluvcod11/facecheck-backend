package com.facecheck.backend.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.facecheck.backend.entity.User;
import com.facecheck.backend.repository.UserRepository;

@RestController
@RequestMapping("/api/mock")
public class MockDataController {

    @Autowired
    private UserRepository userRepository;

    @GetMapping("/create-user")
    public String createMockUser() {
        // เช็กก่อนว่ามีอาจารย์หรือยัง
        if (userRepository.findByUsername("teacher01").isEmpty()) {
            User teacher = new User();
            teacher.setUsername("teacher01");
            teacher.setPasswordHash("password123");
            teacher.setFullName("อาจารย์ ทดสอบ");
            teacher.setRole("teacher");
            userRepository.save(teacher);
        }

        // เช็กก่อนว่ามีนักศึกษาหรือยัง
        if (userRepository.findByUsername("2569000001").isEmpty()) {
            User student = new User();
            student.setUsername("2569000001"); // นักศึกษาใช้รหัสนักศึกษาเป็น username
            student.setPasswordHash("password123");
            student.setFullName("นักศึกษา ทดสอบ");
            student.setRole("student");
            student.setStudentId("2569000001");
            userRepository.save(student);
        }

        return "สร้างข้อมูลจำลองสำเร็จ! ลอง Login ด้วย: <br> Username: 2569000001 / Pass: password123 <br> Username: teacher01 / Pass: password123";
    }
}
