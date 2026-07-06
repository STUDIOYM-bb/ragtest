package com.example.ragtest.ingest.controller;

import com.example.ragtest.admin.job.AdminJob;
import com.example.ragtest.admin.job.AdminJobManager;
import com.example.ragtest.admin.job.AdminJobStatus;
import com.example.ragtest.common.response.ApiResponse;
import com.example.ragtest.ingest.service.ExternalPolicyIngestService;
import com.example.ragtest.ingest.service.IngestOptions;
import com.example.ragtest.ingest.service.IngestResult;
import com.example.ragtest.ingest.service.PolicyIndexingService;
import com.example.ragtest.policy.repository.PolicyRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PolicyIngestControllerTest {

    private final ExternalPolicyIngestService ingestService = mock(ExternalPolicyIngestService.class);
    private final PolicyIndexingService indexingService = mock(PolicyIndexingService.class);
    private final PolicyRepository policyRepository = mock(PolicyRepository.class);
    private final AdminJobManager jobManager = new AdminJobManager();
    private final PolicyIngestController controller = new PolicyIngestController(
            ingestService, indexingService, policyRepository, jobManager, "key", "key", "key", "", "", ""
    );

    @AfterEach
    void tearDown() {
        jobManager.shutdown();
    }

    @Test
    void ingestRequestReturnsJobBeforeCollectionFinishesAndDoesNotIndex() throws Exception {
        CountDownLatch collectionStarted = new CountDownLatch(1);
        CountDownLatch releaseCollection = new CountDownLatch(1);
        when(ingestService.ingestPublicYouthServices(any(IngestOptions.class))).thenAnswer(invocation -> {
            collectionStarted.countDown();
            releaseCollection.await(2, TimeUnit.SECONDS);
            return new IngestResult(1, 1, 0, 0);
        });

        ApiResponse<AdminJob> response = controller.ingestPublicService(1, 20, 50);

        assertThat(response.success()).isTrue();
        assertThat(response.data().getJobId()).isNotBlank();
        assertThat(response.data().getStatus()).isEqualTo(AdminJobStatus.RUNNING);
        assertThat(collectionStarted.await(1, TimeUnit.SECONDS)).isTrue();
        verifyNoInteractions(indexingService);
        releaseCollection.countDown();
    }
}
