package com.gridtrading.repository;

import com.gridtrading.domain.GridLine;
import com.gridtrading.domain.GridLineState;
import com.gridtrading.domain.Strategy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

/**
 * 网格线 Repository
 */
@Repository
public interface GridLineRepository extends JpaRepository<GridLine, Long> {

    /**
     * 查询等待买入的网格线（按买入价从高到低排序）
     */
    List<GridLine> findByStrategyAndStateOrderByBuyPriceDesc(Strategy strategy, GridLineState state);

    /**
     * 查询等待卖出的网格线（按卖出价从低到高排序）
     */
    List<GridLine> findByStrategyAndStateOrderBySellPriceAsc(Strategy strategy, GridLineState state);

    /**
     * 查询策略的最低买入网格价格
     */
    @Query("SELECT MIN(g.buyPrice) FROM GridLine g WHERE g.strategy = :strategy")
    BigDecimal findLowestBuyPrice(@Param("strategy") Strategy strategy);
}
