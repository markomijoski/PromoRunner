package com.promorunner.repository;

import com.promorunner.model.Score;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScoreRepository extends JpaRepository<Score, Long> {

    Optional<Score> findByUserId(Long userId);

    @Query("""
            select s from Score s
            join fetch s.user u
            where u.leaderboardVisible = true
            order by s.bestScore desc
            """)
    List<Score> findTopVisibleByOrderByBestScoreDesc(Pageable pageable);

    @Query("""
            select count(s) from Score s
            join s.user u
            where u.leaderboardVisible = true
            """)
    long countVisiblePlayers();

    long countByBestScoreGreaterThan(int bestScore);

    @Query("select coalesce(avg(s.bestScore), 0) from Score s")
    double averageBestScore();

    @Query("select coalesce(sum(s.totalPlays), 0) from Score s")
    long sumTotalPlays();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Score s where s.user.id = :userId")
    int deleteByUserId(@Param("userId") Long userId);
}
