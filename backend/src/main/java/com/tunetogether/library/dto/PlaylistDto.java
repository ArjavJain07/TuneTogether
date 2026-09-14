package com.tunetogether.library.dto;

import com.tunetogether.common.TrackDto;

import java.util.List;

/** Shape matches the original app's {id, name, tracks, created} playlist object. */
public record PlaylistDto(Long id, String name, boolean liked, List<TrackDto> tracks, long created) {
}
