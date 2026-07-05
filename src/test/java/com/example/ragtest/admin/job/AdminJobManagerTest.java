package com.example.ragtest.admin.job;

import com.example.ragtest.common.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminJobManagerTest {

    private final AdminJobManager manager = new AdminJobManager();

    @AfterEach
    void tearDown() {
        manager.shutdown();
    }

    @Test
    void startsImmediatelyTracksProgressAndPreventsDuplicateType() throws Exception {
        CountDownLatch taskStarted = new CountDownLatch(1);
        CountDownLatch releaseTask = new CountDownLatch(1);

        AdminJob job = manager.start(
                AdminJobType.INGEST_ALL,
                "시작",
                "완료",
                progress -> {
                    progress.update(40, "지자체복지서비스 수집 중...");
                    taskStarted.countDown();
                    releaseTask.await(2, TimeUnit.SECONDS);
                    return "result";
                }
        );

        assertThat(taskStarted.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(job.getStatus()).isEqualTo(AdminJobStatus.RUNNING);
        assertThat(job.getProgressPercent()).isEqualTo(40);
        assertThatThrownBy(() -> manager.start(AdminJobType.INGEST_ALL, "중복", "완료", progress -> null))
                .isInstanceOf(BusinessException.class);

        releaseTask.countDown();
        waitUntilFinished(job);

        assertThat(job.getStatus()).isEqualTo(AdminJobStatus.SUCCESS);
        assertThat(job.getProgressPercent()).isEqualTo(100);
        assertThat(job.getResult()).isEqualTo("result");
        assertThat(manager.latest()).contains(job);
    }

    private void waitUntilFinished(AdminJob job) throws InterruptedException {
        for (int attempt = 0; attempt < 100 && job.getStatus() == AdminJobStatus.RUNNING; attempt++) {
            Thread.sleep(10);
        }
    }
}
