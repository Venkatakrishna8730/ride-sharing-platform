package com.rideplatform.trip.domain.valueobject;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GeoLocationTest {

    @Test
    @DisplayName("Should create valid GeoLocation within bounds")
    void shouldCreateValidGeoLocation() {
        GeoLocation loc = new GeoLocation(37.7749, -122.4194);
        assertEquals(37.7749, loc.getLatitude());
        assertEquals(-122.4194, loc.getLongitude());
    }

    @Test
    @DisplayName("Should reject invalid latitude out of bounds")
    void shouldRejectInvalidLatitude() {
        assertThrows(IllegalArgumentException.class, () -> new GeoLocation(95.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new GeoLocation(-91.0, 0.0));
    }

    @Test
    @DisplayName("Should reject invalid longitude out of bounds")
    void shouldRejectInvalidLongitude() {
        assertThrows(IllegalArgumentException.class, () -> new GeoLocation(0.0, 185.0));
        assertThrows(IllegalArgumentException.class, () -> new GeoLocation(0.0, -181.0));
    }

    @Test
    @DisplayName("Should calculate accurate distance using Haversine formula")
    void shouldCalculateAccurateDistance() {
        // San Francisco to Oakland: ~13 km
        GeoLocation sf = new GeoLocation(37.7749, -122.4194);
        GeoLocation oakland = new GeoLocation(37.8044, -122.2712);

        double distance = sf.distanceTo(oakland);
        assertTrue(distance > 12.0 && distance < 15.0, "Expected distance ~13km but got: " + distance);
    }
}
