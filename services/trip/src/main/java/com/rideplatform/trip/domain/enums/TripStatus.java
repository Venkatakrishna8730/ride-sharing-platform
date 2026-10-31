package com.rideplatform.trip.domain.enums;

public enum TripStatus {
    REQUESTED,
    MATCHING,
    ACCEPTED,
    ARRIVING,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED;

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED;
    }
}
