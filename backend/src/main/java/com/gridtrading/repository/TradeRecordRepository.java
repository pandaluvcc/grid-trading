package com.gridtrading.repository;

import com.gridtrading.domain.Strategy;
import com.gridtrading.domain.TradeRecord;
import com.gridtrading.domain.TradeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 交易记录 Repository
 */
@Repository
public interface TradeRecordRepository extends JpaRepository<TradeRecord, Long> {

    /**
     * 查询策略的所有交易记录（按时间倒序）
     */
    List<TradeRecord> findByStrategyOrderByTradeTimeDesc(Strategy strategy);

    /**
     * 查询策略的指定类型交易记录
     */
    List<TradeRecord> findByStrategyAndType(Strategy strategy, TradeType type);

    /**
     * 查询策略在指定时间之后的交易记录
     */
    List<TradeRecord> findByStrategyIdAndTradeTimeAfter(Long strategyId, LocalDateTime time);

    /**
     * 通过策略ID查询交易记录（用于重复性检查）
     */
    List<TradeRecord> findByStrategyId(Long strategyId);

    /**
     * 查询某个网格的所有交易记录（按时间升序）
     */
    List<TradeRecord> findByGridLineIdOrderByTradeTimeAsc(Long gridLineId);
}
