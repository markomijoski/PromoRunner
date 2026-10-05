package com.promorunner.repository;

import com.promorunner.model.CampaignWinnerNotification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampaignWinnerNotificationRepository extends JpaRepository<CampaignWinnerNotification, Long> {

    boolean existsByPlaceRank(int placeRank);
}
