package com.rideplatform.trip.domain.state;

import com.rideplatform.trip.domain.enums.TripStatus;

import java.util.EnumMap;
import java.util.Map;

public class TripStateFactory {

    private static final Map<TripStatus, TripState> STATES = new EnumMap<>(TripStatus.class);

    static {
        STATES.put(TripStatus.REQUESTED, new RequestedState());
        STATES.put(TripStatus.MATCHING, new MatchingState());
        STATES.put(TripStatus.ACCEPTED, new AcceptedState());
        STATES.put(TripStatus.ARRIVING, new ArrivingState());
        STATES.put(TripStatus.IN_PROGRESS, new InProgressState());
        STATES.put(TripStatus.COMPLETED, new CompletedState());
        STATES.put(TripStatus.CANCELLED, new CancelledState());
    }

    public static TripState getState(TripStatus status) {
        TripState state = STATES.get(status);
        if (state == null) {
            throw new IllegalArgumentException("Unknown trip status: " + status);
        }
        return state;
    }
}
