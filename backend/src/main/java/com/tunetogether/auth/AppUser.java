package com.tunetogether.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * A registered (Google-authenticated) user. Guests never get a row here - that
 * mirrors the original app, where "Guest Mode" had no backend account at all.
 */
@Entity
@Table(name = "app_user", uniqueConstraints = {
        @UniqueConstraint(name = "uk_app_user_google_sub", columnNames = "google_sub"),
        @UniqueConstraint(name = "uk_app_user_email", columnNames = "email")
})
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "google_sub", nullable = false, updatable = false)
    private String googleSub;

    @Column(nullable = false)
    private String email;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "picture_url")
    private String pictureUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_login_at", nullable = false)
    private Instant lastLoginAt;

    protected AppUser() {
        // JPA
    }

    public AppUser(String googleSub, String email, String displayName, String pictureUrl) {
        this.googleSub = googleSub;
        this.email = email;
        this.displayName = displayName;
        this.pictureUrl = pictureUrl;
        Instant now = Instant.now();
        this.createdAt = now;
        this.lastLoginAt = now;
    }

    public void touchLogin(String displayName, String pictureUrl) {
        this.displayName = displayName;
        this.pictureUrl = pictureUrl;
        this.lastLoginAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getGoogleSub() {
        return googleSub;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPictureUrl() {
        return pictureUrl;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }
}
