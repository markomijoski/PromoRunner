package com.promorunner.repository;

import com.promorunner.model.MarketingConsent;
import com.promorunner.model.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MarketingConsentRepository extends JpaRepository<MarketingConsent, Long> {

    Optional<MarketingConsent> findFirstByUserIdOrderByConsentedAtDescIdDesc(Long userId);

    boolean existsByUserId(Long userId);

    /**
     * Users whose latest consent row is an active opt-in (not withdrawn).
     */
    @Query("""
            select c.user from MarketingConsent c
            where c.consented = true
              and c.withdrawnAt is null
              and c.consentedAt = (
                  select max(c2.consentedAt) from MarketingConsent c2 where c2.user = c.user
              )
            """)
    List<User> findActiveLeadUsers();

    @Query("""
            select count(distinct c.user.id) from MarketingConsent c
            where c.consented = true
              and c.withdrawnAt is null
              and c.consentedAt = (
                  select max(c2.consentedAt) from MarketingConsent c2 where c2.user = c.user
              )
            """)
    long countActiveLeads();
}
