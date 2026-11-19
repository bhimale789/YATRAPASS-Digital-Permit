package com.example.touristpermit.area.dto;

import jakarta.validation.constraints.NotNull;

public record AreaStatusRequest(@NotNull Boolean active) {
}
