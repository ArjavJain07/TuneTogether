package com.tunetogether.library.dto;

import com.tunetogether.common.TrackDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record AddTrackRequest(@Valid @NotNull TrackDto track) {
}
