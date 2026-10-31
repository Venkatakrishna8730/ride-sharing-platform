package com.rideplatform.trip.domain;

import com.rideplatform.trip.domain.entity.Trip;
import com.rideplatform.trip.domain.enums.TripStatus;
import com.rideplatform.trip.domain.valueobject.GeoLocation;
import com.rideplatform.trip.domain.valueobject.Money;
import com.rideplatform.trip.exception.InvalidStateTransitionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TripStateTransitionTest {

    private Trip trip;
    private final UUID riderId = UUID.randomUUID();
    private final UUID driverId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        trip = Trip.builder()
                .id(UUID.randomUUID())
                .riderId(riderId)
                .status(TripStatus.REQUESTED)
                .pickupLocation(new GeoLocation(37.7749, -122.4194))
                .dropoffLocation(new GeoLocation(37.7891, -122.4014))
                .fareEstimate(Money.of(15.50))
                .build();
    }

    @Test
    @DisplayName("Should successfully follow full lifecycle from REQUESTED to COMPLETED")
    void shouldFollowValidFullLifecycle() {
        assertEquals(TripStatus.REQUESTED, trip.getStatus());

        // REQUESTED -> MATCHING
        trip.toMatching();
        assertEquals(TripStatus.MATCHING, trip.getStatus());

        // MATCHING -> ACCEPTED (assigns driver)
        trip.accept(driverId);
        assertEquals(TripStatus.ACCEPTED, trip.getStatus());
        assertEquals(driverId, trip.getDriverId());

        // ACCEPTED -> ARRIVING
        trip.arrive();
        assertEquals(TripStatus.ARRIVING, trip.getStatus());

        // ARRIVING -> IN_PROGRESS
        trip.startTrip();
        assertEquals(TripStatus.IN_PROGRESS, trip.getStatus());

        // IN_PROGRESS -> COMPLETED
        trip.complete();
        assertEquals(TripStatus.COMPLETED, trip.getStatus());
        assertNotNull(trip.getActualFare());
    }

    @Test
    @DisplayName("Should reject illegal transition jumping from REQUESTED directly to COMPLETED")
    void shouldRejectIllegalTransitionDirectToCompleted() {
        assertEquals(TripStatus.REQUESTED, trip.getStatus());

        InvalidStateTransitionException ex = assertThrows(
                InvalidStateTransitionException.class,
                () -> trip.complete()
        );

        assertTrue(ex.getMessage().contains("Cannot transition from 'REQUESTED' to 'COMPLETED'"));
        assertEquals(TripStatus.REQUESTED, trip.getStatus());
    }

    @Test
    @DisplayName("Should reject illegal transition jumping from REQUESTED directly to IN_PROGRESS")
    void shouldRejectIllegalTransitionDirectToInProgress() {
        assertThrows(InvalidStateTransitionException.class, () -> trip.startTrip());
        assertEquals(TripStatus.REQUESTED, trip.getStatus());
    }

    @Test
    @DisplayName("Should allow cancellation from REQUESTED state")
    void shouldAllowCancellationFromRequested() {
        trip.cancel("Rider changed mind", "RIDER");
        assertEquals(TripStatus.CANCELLED, trip.getStatus());
        assertEquals("Rider changed mind", trip.getCancellationReason());
        assertEquals("RIDER", trip.getCancelledBy());
    }

    @Test
    @DisplayName("Should forbid cancellation once trip is IN_PROGRESS")
    void shouldForbidCancellationWhenInProgress() {
        trip.toMatching();
        trip.accept(driverId);
        trip.arrive();
        trip.startTrip();
        assertEquals(TripStatus.IN_PROGRESS, trip.getStatus());

        assertThrows(InvalidStateTransitionException.class, () -> trip.cancel("Tried to cancel in ride", "RIDER"));
        assertEquals(TripStatus.IN_PROGRESS, trip.getStatus());
    }
}
