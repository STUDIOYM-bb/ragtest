package com.example.ragtest.admin.job;

import java.time.LocalDateTime;

public class AdminJob {

    private final String jobId;
    private final AdminJobType type;
    private volatile AdminJobStatus status;
    private volatile int progressPercent;
    private volatile String message;
    private volatile Object result;
    private volatile String errorMessage;
    private final LocalDateTime startedAt;
    private volatile LocalDateTime finishedAt;

    private AdminJob(String jobId, AdminJobType type, String message) {
        this.jobId = jobId;
        this.type = type;
        this.status = AdminJobStatus.RUNNING;
        this.progressPercent = 0;
        this.message = message;
        this.startedAt = LocalDateTime.now();
    }

    public static AdminJob running(String jobId, AdminJobType type, String message) {
        return new AdminJob(jobId, type, message);
    }

    public synchronized void updateProgress(int progressPercent, String message) {
        if (status != AdminJobStatus.RUNNING) {
            return;
        }
        this.progressPercent = Math.max(this.progressPercent, Math.min(99, Math.max(0, progressPercent)));
        if (message != null && !message.isBlank()) {
            this.message = message;
        }
    }

    synchronized void succeed(Object result, String message) {
        this.result = result;
        this.status = AdminJobStatus.SUCCESS;
        this.progressPercent = 100;
        this.message = message;
        this.finishedAt = LocalDateTime.now();
    }

    synchronized void fail(Throwable throwable) {
        this.status = AdminJobStatus.FAILED;
        this.message = "작업에 실패했습니다.";
        this.errorMessage = throwable.getMessage() == null ? throwable.getClass().getSimpleName() : throwable.getMessage();
        this.finishedAt = LocalDateTime.now();
    }

    public String getJobId() {
        return jobId;
    }

    public AdminJobType getType() {
        return type;
    }

    public AdminJobStatus getStatus() {
        return status;
    }

    public int getProgressPercent() {
        return progressPercent;
    }

    public String getMessage() {
        return message;
    }

    public Object getResult() {
        return result;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }
}
