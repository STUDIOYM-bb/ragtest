package com.example.ragtest.admin.job;

import com.example.ragtest.common.exception.BusinessException;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

@Component
public class AdminJobManager {

    private static final int MAX_HISTORY_SIZE = 100;

    private final ConcurrentHashMap<String, AdminJob> jobs = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(2, new AdminJobThreadFactory());
    private final Object startLock = new Object();

    public AdminJob start(AdminJobType type, String startMessage, String successMessage, AdminJobTask task) {
        AdminJob job;
        synchronized (startLock) {
            boolean duplicateRunning = jobs.values().stream()
                    .anyMatch(existing -> existing.getStatus() == AdminJobStatus.RUNNING
                            && conflicts(existing.getType(), type));
            if (duplicateRunning) {
                throw new BusinessException(type + " 작업과 충돌하는 관리자 작업이 이미 실행 중입니다.");
            }
            job = AdminJob.running(UUID.randomUUID().toString(), type, startMessage);
            jobs.put(job.getJobId(), job);
            pruneHistory();
        }

        executor.submit(() -> {
            try {
                Object result = task.execute(job::updateProgress);
                job.succeed(result, successMessage);
            } catch (Throwable throwable) {
                job.fail(throwable);
            }
        });
        return job;
    }

    public AdminJob get(String jobId) {
        AdminJob job = jobs.get(jobId);
        if (job == null) {
            throw new BusinessException("작업을 찾을 수 없습니다: " + jobId);
        }
        return job;
    }

    public List<AdminJob> latest() {
        return jobs.values().stream()
                .sorted(Comparator.comparing(AdminJob::getStartedAt).reversed())
                .limit(10)
                .toList();
    }

    public List<AdminJob> running() {
        return jobs.values().stream()
                .filter(job -> job.getStatus() == AdminJobStatus.RUNNING)
                .sorted(Comparator.comparing(AdminJob::getStartedAt).reversed())
                .toList();
    }

    private void pruneHistory() {
        if (jobs.size() <= MAX_HISTORY_SIZE) {
            return;
        }
        jobs.values().stream()
                .filter(job -> job.getStatus() != AdminJobStatus.RUNNING)
                .sorted(Comparator.comparing(AdminJob::getStartedAt))
                .limit(jobs.size() - MAX_HISTORY_SIZE)
                .map(AdminJob::getJobId)
                .forEach(jobs::remove);
    }

    private boolean conflicts(AdminJobType runningType, AdminJobType requestedType) {
        if (runningType == requestedType) {
            return true;
        }
        boolean runningIndex = runningType == AdminJobType.INDEX_REAL || runningType == AdminJobType.REINDEX_REAL;
        boolean requestedIndex = requestedType == AdminJobType.INDEX_REAL || requestedType == AdminJobType.REINDEX_REAL;
        if (runningIndex && requestedIndex) {
            return true;
        }
        boolean runningIngest = runningType.name().startsWith("INGEST_");
        boolean requestedIngest = requestedType.name().startsWith("INGEST_");
        return runningIngest && requestedIngest
                && (runningType == AdminJobType.INGEST_ALL || requestedType == AdminJobType.INGEST_ALL);
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    @FunctionalInterface
    public interface AdminJobTask {
        Object execute(AdminJobProgress progress) throws Exception;
    }

    @FunctionalInterface
    public interface AdminJobProgress {
        void update(int progressPercent, String message);
    }

    private static class AdminJobThreadFactory implements ThreadFactory {
        private int sequence = 1;

        @Override
        public synchronized Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "admin-job-" + sequence++);
            thread.setDaemon(true);
            return thread;
        }
    }
}
