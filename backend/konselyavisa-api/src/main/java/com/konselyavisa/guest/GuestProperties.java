package com.konselyavisa.guest;

import com.konselyavisa.organization.DemoOrganization;
import java.time.Duration;
import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "konselyavisa.guest")
public class GuestProperties {

    private Duration ticketTtl = Duration.ofMinutes(30);
    private UUID defaultOrganizationId = DemoOrganization.ID;

    public Duration getTicketTtl() {
        return ticketTtl;
    }

    public void setTicketTtl(Duration ticketTtl) {
        this.ticketTtl = ticketTtl;
    }

    public UUID getDefaultOrganizationId() {
        return defaultOrganizationId;
    }

    public void setDefaultOrganizationId(UUID defaultOrganizationId) {
        this.defaultOrganizationId = defaultOrganizationId;
    }
}
