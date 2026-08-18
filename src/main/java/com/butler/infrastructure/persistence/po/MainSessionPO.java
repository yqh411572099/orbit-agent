package com.butler.infrastructure.persistence.po;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "main_session", uniqueConstraints = @UniqueConstraint(columnNames = "user_id"))
public class MainSessionPO {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
    private String city;
    private String latitude;
    private String longitude;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getLatitude() { return latitude; }
    public void setLatitude(String latitude) { this.latitude = latitude; }
    public String getLongitude() { return longitude; }
    public void setLongitude(String longitude) { this.longitude = longitude; }
}
