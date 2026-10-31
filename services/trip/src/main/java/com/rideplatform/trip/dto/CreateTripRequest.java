package com.rideplatform.trip.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTripRequest {

    @NotNull(message = "Pickup latitude is required")
    @DecimalMin(value = "-90.0", message = "Pickup latitude must be >= -90.0")
    @DecimalMax(value = "90.0", message = "Pickup latitude must be <= 90.0")
    private Double pickupLatitude;

    @NotNull(message = "Pickup longitude is required")
    @DecimalMin(value = "-180.0", message = "Pickup longitude must be >= -180.0")
    @DecimalMax(value = "180.0", message = "Pickup longitude must be <= 180.0")
    private Double pickupLongitude;

    private String pickupAddress;

    @NotNull(message = "Dropoff latitude is required")
    @DecimalMin(value = "-90.0", message = "Dropoff latitude must be >= -90.0")
    @DecimalMax(value = "90.0", message = "Dropoff latitude must be <= 90.0")
    private Double dropoffLatitude;

    @NotNull(message = "Dropoff longitude is required")
    @DecimalMin(value = "-180.0", message = "Dropoff longitude must be >= -180.0")
    @DecimalMax(value = "180.0", message = "Dropoff longitude must be <= 180.0")
    private Double dropoffLongitude;

    private String dropoffAddress;

    private BigDecimal fareEstimate;
    
    @Builder.Default
    private String currency = "USD";
}
