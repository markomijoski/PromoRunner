package com.promorunner.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "marketing_consents")
@Getter
@Setter
@NoArgsConstructor
public class MarketingConsent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private boolean consented;

    @Column(name = "consent_version", nullable = false, length = 20)
    private String consentVersion = "1.0";

    @Column(name = "user_agent", columnDefinition = "text")
    private String userAgent;

    @Column(name = "consented_at", nullable = false, updatable = false)
    private Instant consentedAt;

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    @PrePersist
    void onCreate() {
        if (consentedAt == null) {
            consentedAt = Instant.now();
        }
        if (consentVersion == null) {
            consentVersion = "1.0";
        }
    }
}
