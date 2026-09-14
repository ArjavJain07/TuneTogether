package com.tunetogether.music.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record YouTubeSearchResponse(List<Item> items) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(Id id) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Id(String videoId) {
    }
}
