package com.konselyavisa.outbox;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherConfigTest {

    @Mock
    private OutboxPublisher publisher;

    @Test
    void pollInvokesPublishDue() {
        OutboxPublisherConfig.Scheduler scheduler = new OutboxPublisherConfig.Scheduler(publisher);

        scheduler.poll();

        verify(publisher).publishDue();
    }
}
