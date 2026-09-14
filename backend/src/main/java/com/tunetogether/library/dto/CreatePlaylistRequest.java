package com.tunetogether.library.dto;

import jakarta.validation.constraints.NotBlank;

public record CreatePlaylistRequest(@NotBlank String name) {
}
