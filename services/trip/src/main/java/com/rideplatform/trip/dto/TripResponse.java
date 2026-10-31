package com.rideplatform.trip.dto;

import com.rideplatform.trip.domain.entity.Trip;
import com.rideplatform.trip.domain.enums.TripStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripResponse {
    private UUID id;
    private UUID riderId;
    private UUID driverId;
    private TripStatus status;
    private Double pickupLatitude;
    private Double pickupLongitude;
    private String pickupAddress;
    private Double dropoffLatitude;
    private Double dropoffLongitude;
    private String dropoffAddress;
    private Double distanceKm;
    private BigDecimal fareEstimate;
    private BigDecimal actualFare;
    private String currency;
    private String cancellationReason;
    private String cancelledBy;
    private Instant createdAt;
    private Instant updatedAt;

    public static TripResponse fromEntity(Trip trip) {
        Double distance = null;
        if (trip.getPickupLocation() != null && trip.getDropoffLocation() != null) {
            distance = Math.round(trip.getPickupLocation().distanceTo(trip.getDropoffLocation()) * 100.0) / 100.0;
        }

        return TripResponse.builder()
                .id(trip.getId())
                .riderId(trip.getRiderId())
                .driverId(trip.getDriverId())
                .status(trip.getStatus())
                .pickupLatitude(trip.getPickupLocation() != null ? trip.getPickupLocation().getLatitude() : null)
                .pickupLongitude(trip.getPickupLocation() != null ? trip.getPickupLocation().getLongitude() : null)
                .pickupAddress(trip.getPickupAddress())
                .dropoffLatitude(trip.getDropoffLocation() != null ? trip.getDropoffLocation().getLatitude() : null)
                .dropoffLongitude(trip.getDropoffLocation() != null ? trip.getDropoffLocation().getLongitude() : null)
                .dropoffAddress(trip.getDropoffAddress())
                .distanceKm(distance)
                .fareEstimate(trip.getFareEstimate() != null ? trip.getFareEstimate().getAmount() : null)
                .actualFare(trip.getActualFare() != null ? trip.getActualFare().getAmount() : null)
                .currency(trip.getFareEstimate() != null ? trip.getFareEstimate().getCurrency() : "USD")
                .cancellationReason(trip.getCancellationReason())
                .cancelledBy(trip.getCancelledBy())
                .createdAt(trip.getCreatedAt())
                .updatedAt(trip.getUpdatedAt())
                .build();
    }
}
