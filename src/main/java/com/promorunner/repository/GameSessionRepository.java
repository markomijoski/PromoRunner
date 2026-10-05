package com.promorunner.repository;

import com.promorunner.model.GameSession;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GameSessionRepository extends JpaRepository<GameSession, Long> {

    Optional<GameSession> findByIdAndUserId(Long id, Long userId);

    List<GameSession> findByUserIdOrderByStartedAtDesc(Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from GameSession g where g.user.id = :userId")
    int deleteByUserId(@Param("userId") Long userId);
}
