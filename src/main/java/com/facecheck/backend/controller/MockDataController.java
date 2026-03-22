package com.facecheck.backend.controller;

import com.facecheck.backend.entity.User;
import com.facecheck.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mock")
public class MockDataController {

    @Autowired
    private UserRepository userRepository;

    @GetMapping("/create-user")
    public String createMockUser() {
        // เช็กก่อนว่ามีอาจารย์หรือยัง
        if (userRepository.findByEmail("teacher@utcc.ac.th").isEmpty()) {
            User teacher = new User();
            teacher.setEmail("teacher@utcc.ac.th");
            teacher.setPasswordHash("password123");
            teacher.setFullName("อาจารย์ ทดสอบ");
            teacher.setRole("teacher");
            userRepository.save(teacher);
        }

        // เช็กก่อนว่ามีนักศึกษาหรือยัง
        if (userRepository.findByEmail("student@utcc.ac.th").isEmpty()) {
            User student = new User();
            student.setEmail("student@utcc.ac.th");
            student.setPasswordHash("password123");
            student.setFullName("นักศึกษา ทดสอบ");
            student.setRole("student");
            student.setStudentId("2569000001");
            userRepository.save(student);
        }

        return "สร้างข้อมูลจำลองสำเร็จ! ลอง Login ด้วย: <br> Email: student@utcc.ac.th / Pass: password123 <br> Email: teacher@utcc.ac.th / Pass: password123";
    }
}
