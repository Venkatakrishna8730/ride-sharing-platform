package com.rideplatform.trip.domain.state;

import com.rideplatform.trip.domain.enums.TripStatus;

import java.util.UUID;

public interface TripContext {
    UUID getId();
    TripStatus getStatus();
    void transitionTo(TripState nextState);
    void assignDriver(UUID driverId);
    void applyCancellation(String reason, String cancelledBy);
}
