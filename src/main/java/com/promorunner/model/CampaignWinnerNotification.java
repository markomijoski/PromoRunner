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
@Table(name = "campaign_winner_notifications")
@Getter
@Setter
@NoArgsConstructor
public class CampaignWinnerNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 1–3 for winners; 0 marks an empty-board completion sentinel. */
    @Column(name = "place_rank", nullable = false, unique = true)
    private int placeRank;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "prize_name", nullable = false, length = 255)
    private String prizeName;

    @Column(name = "email_sent_at", nullable = false)
    private Instant emailSentAt;

    @PrePersist
    void onCreate() {
        if (emailSentAt == null) {
            emailSentAt = Instant.now();
        }
    }
}
