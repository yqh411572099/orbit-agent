package com.butler.domain.model;

import java.time.Instant;

public class MainSession {
    private final Long id;
    private final Long userId;
    private final Instant createdAt;
    private final String city;
    private final String latitude;
    private final String longitude;

    public MainSession(Long id, Long userId, Instant createdAt) {
        this(id, userId, createdAt, null, null, null);
    }

    public MainSession(Long id, Long userId, Instant createdAt, String city, String latitude, String longitude) {
        this.id = id;
        this.userId = userId;
        this.createdAt = createdAt;
        this.city = city;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Instant getCreatedAt() { return createdAt; }
    public String getCity() { return city; }
    public String getLatitude() { return latitude; }
    public String getLongitude() { return longitude; }
}
