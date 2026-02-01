package com.gridtrading.repository;

import com.gridtrading.domain.Strategy;
import com.gridtrading.domain.StrategyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 策略 Repository
 */
@Repository
public interface StrategyRepository extends JpaRepository<Strategy, Long> {

    /**
     * 查询指定状态的策略列表
     */
    List<Strategy> findByStatus(StrategyStatus status);

    /**
     * 加载策略及其网格线（使用 JOIN FETCH 避免 N+1 问题）
     */
    @Query("""
            select distinct s
            from Strategy s
            left join fetch s.gridLines
            where s.id = :id
            """)
    Optional<Strategy> findByIdWithGridLines(@Param("id") Long id);
}
