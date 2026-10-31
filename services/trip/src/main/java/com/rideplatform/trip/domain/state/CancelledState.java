package com.rideplatform.trip.domain.state;

import com.rideplatform.trip.domain.enums.TripStatus;

public class CancelledState implements TripState {

    @Override
    public TripStatus getStatus() {
        return TripStatus.CANCELLED;
    }
}
