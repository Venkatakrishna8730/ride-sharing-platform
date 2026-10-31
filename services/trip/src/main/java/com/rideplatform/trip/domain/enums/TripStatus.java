package com.rideplatform.trip.domain.enums;

import com.rideplatform.trip.exception.InvalidStateTransitionException;

import java.util.Map;
import java.util.Set;

public enum TripStatus {
    REQUESTED,
    MATCHING,
    ACCEPTED,
    ARRIVING,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED;

    // Declarative transition matrix: State -> Allowed Target States
    private static final Map<TripStatus, Set<TripStatus>> ALLOWED_TRANSITIONS = Map.of(
        REQUESTED, Set.of(MATCHING, CANCELLED),
        MATCHING, Set.of(ACCEPTED, CANCELLED),
        ACCEPTED, Set.of(ARRIVING, CANCELLED),
        ARRIVING, Set.of(IN_PROGRESS, CANCELLED),
        IN_PROGRESS, Set.of(COMPLETED),
        COMPLETED, Set.of(),
        CANCELLED, Set.of()
    );

    public boolean canTransitionTo(TripStatus target) {
        if (target == null) return false;
        return ALLOWED_TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }

    public void validateTransition(TripStatus target) {
        if (!canTransitionTo(target)) {
            throw new InvalidStateTransitionException(this, target);
        }
    }

    public Set<TripStatus> getAllowedTransitions() {
        return ALLOWED_TRANSITIONS.getOrDefault(this, Set.of());
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED;
    }
}
