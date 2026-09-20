package com.promorunner.repository;

import com.promorunner.model.Score;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ScoreRepository extends JpaRepository<Score, Long> {

    Optional<Score> findByUserId(Long userId);

    @Query("""
            select s from Score s
            join fetch s.user
            order by s.bestScore desc
            """)
    List<Score> findTopByOrderByBestScoreDesc(Pageable pageable);

    long countByBestScoreGreaterThan(int bestScore);

    @Query("select coalesce(avg(s.bestScore), 0) from Score s")
    double averageBestScore();

    @Query("select coalesce(sum(s.totalPlays), 0) from Score s")
    long sumTotalPlays();
}
