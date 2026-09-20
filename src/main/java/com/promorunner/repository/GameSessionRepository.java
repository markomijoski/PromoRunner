package com.promorunner.repository;

import com.promorunner.model.GameSession;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameSessionRepository extends JpaRepository<GameSession, Long> {

    Optional<GameSession> findByIdAndUserId(Long id, Long userId);
}
