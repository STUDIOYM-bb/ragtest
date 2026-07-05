package com.example.ragtest.admin.controller;

import com.example.ragtest.admin.job.AdminJob;
import com.example.ragtest.admin.job.AdminJobManager;
import com.example.ragtest.common.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/jobs")
public class AdminJobController {

    private final AdminJobManager adminJobManager;

    public AdminJobController(AdminJobManager adminJobManager) {
        this.adminJobManager = adminJobManager;
    }

    @GetMapping("/{jobId}")
    public ApiResponse<AdminJob> findById(@PathVariable String jobId) {
        return ApiResponse.ok(adminJobManager.get(jobId), "작업 상태 조회 완료");
    }

    @GetMapping("/latest")
    public ApiResponse<List<AdminJob>> latest() {
        return ApiResponse.ok(adminJobManager.latest(), "최근 작업 조회 완료");
    }

    @GetMapping("/running")
    public ApiResponse<List<AdminJob>> running() {
        return ApiResponse.ok(adminJobManager.running(), "실행 중인 작업 조회 완료");
    }
}
