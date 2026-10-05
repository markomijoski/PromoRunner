package com.promorunner.repository;

import com.promorunner.model.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    @Query("""
            select count(u) > 0 from User u
            where lower(u.displayName) = lower(:username)
              and (:excludeId is null or u.id <> :excludeId)
            """)
    boolean existsByDisplayNameIgnoreCaseExcludingId(
            @Param("username") String username,
            @Param("excludeId") Long excludeId);

    List<User> findByMarketingConsentTrue();

    long countByMarketingConsentTrue();
}
