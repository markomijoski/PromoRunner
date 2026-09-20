package com.promorunner.repository;

import com.promorunner.model.MagicLinkToken;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MagicLinkTokenRepository extends JpaRepository<MagicLinkToken, Long> {

    Optional<MagicLinkToken> findByTokenHash(String tokenHash);

    @Query("""
            select t from MagicLinkToken t
            where t.user.id = :userId and t.usedAt is null
            """)
    List<MagicLinkToken> findUnusedByUserId(@Param("userId") Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update MagicLinkToken t
            set t.usedAt = CURRENT_TIMESTAMP
            where t.user.id = :userId and t.usedAt is null
            """)
    int invalidateUnusedForUser(@Param("userId") Long userId);
}
