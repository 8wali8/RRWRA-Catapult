package com.streaming.analytics.graphql.model;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class Stream {
    private String id;
    private String title;
    private String streamerName;
    private String category;
    private int viewerCount;
    private boolean isLive;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String quality;
    private int bitrate;
    private int fps;
    private String description;
    private String[] tags;
    private String language;
    private String thumbnailUrl;
    private String streamUrl;
}