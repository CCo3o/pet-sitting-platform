package com.petsitting.booking;

/** Authoritative workflow state; the legacy status column is only for migration compatibility. */
public enum OrderState {
    PENDING, ACCEPTED, IN_PROGRESS, WAITING_CONFIRM, COMPLETED, CANCELLED, REJECTED, EXPIRED
}
