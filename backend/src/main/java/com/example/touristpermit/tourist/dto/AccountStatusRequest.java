package com.example.touristpermit.tourist.dto;

import jakarta.validation.constraints.NotNull;

public record AccountStatusRequest(@NotNull Boolean enabled) {
}
