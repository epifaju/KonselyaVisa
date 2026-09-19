package com.konselyavisa.outbox.domain;

public final class OutboxEventTypes {

    public static final String CASE_CREATED = "CASE_CREATED";
    public static final String DOCUMENT_UPLOADED = "DOCUMENT_UPLOADED";
    public static final String PAYMENT_COMPLETED = "PAYMENT_COMPLETED";
    public static final String APPOINTMENT_BOOKED = "APPOINTMENT_BOOKED";
    public static final String APPOINTMENT_CANCELLED = "APPOINTMENT_CANCELLED";
    public static final String CORRECTION_REQUESTED = "CORRECTION_REQUESTED";
    public static final String CASE_COMPLETED = "CASE_COMPLETED";
    public static final String DATA_DELETION_REQUESTED = "DATA_DELETION_REQUESTED";

    public static final String AGGREGATE_CASE = "CASE";
    public static final String AGGREGATE_PRIVACY = "PRIVACY";

    private OutboxEventTypes() {}
}
