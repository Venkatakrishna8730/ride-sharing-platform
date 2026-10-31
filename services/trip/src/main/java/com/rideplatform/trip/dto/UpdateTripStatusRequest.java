package com.rideplatform.trip.dto;

import com.rideplatform.trip.domain.enums.TripStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTripStatusRequest {

    @NotNull(message = "Target status is required")
    private TripStatus targetStatus;

    private UUID driverId;
    private String cancellationReason;
}
