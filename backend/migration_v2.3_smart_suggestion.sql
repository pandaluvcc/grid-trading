-- ====================================
-- 智能建议系统数据库迁移脚本 v2.3
-- ====================================

-- 1. 为 strategy 表添加最新价格字段
ALTER TABLE strategy ADD COLUMN IF NOT EXISTS last_price DECIMAL(20,8) DEFAULT NULL COMMENT '最新市场价格';

-- 2. 为 strategy 表添加最大持仓比例字段
ALTER TABLE strategy ADD COLUMN IF NOT EXISTS max_position_ratio DECIMAL(5,4) DEFAULT 0.8000 COMMENT '最大持仓比例，默认80%';

-- 3. 为 grid_line 表添加暂缓相关字段（预留，暂不启用）
ALTER TABLE grid_line ADD COLUMN IF NOT EXISTS deferred BOOLEAN DEFAULT FALSE COMMENT '是否暂缓买入';
ALTER TABLE grid_line ADD COLUMN IF NOT EXISTS deferred_reason VARCHAR(100) DEFAULT NULL COMMENT '暂缓原因';
ALTER TABLE grid_line ADD COLUMN IF NOT EXISTS deferred_at TIMESTAMP DEFAULT NULL COMMENT '暂缓时间';

-- 说明：暂缓功能暂时不实现，先预留字段

