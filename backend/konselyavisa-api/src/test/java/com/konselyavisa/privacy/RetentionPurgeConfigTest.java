package com.konselyavisa.privacy;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RetentionPurgeConfigTest {

    @Mock
    private RetentionPurgeService retentionPurgeService;

    @Test
    void pollInvokesPurgeDue() {
        RetentionPurgeConfig.Scheduler scheduler = new RetentionPurgeConfig.Scheduler(retentionPurgeService);

        scheduler.poll();

        verify(retentionPurgeService).purgeDue();
    }
}
