-- 功能增强 v2.1 - 策略信息完整化 + 实际成交价机制
-- 执行日期: 2026-02-07

-- 1. 为 Strategy 表添加网格模型版本和摘要字段
ALTER TABLE strategy 
ADD COLUMN grid_model_version VARCHAR(20) DEFAULT 'v2.0' COMMENT '网格模型版本',
ADD COLUMN grid_summary VARCHAR(100) DEFAULT '小网13/中网4/大网2' COMMENT '网格分布摘要';

-- 2. 为 GridLine 表添加实际成交价字段
ALTER TABLE grid_line 
ADD COLUMN actual_buy_price DECIMAL(20, 8) NULL COMMENT '实际买入价（用户可编辑）',
ADD COLUMN actual_sell_price DECIMAL(20, 8) NULL COMMENT '实际卖出价（实际成交价）';

-- 3. 为现有策略补充默认值
UPDATE strategy 
SET grid_model_version = 'v2.0', 
    grid_summary = '小网13/中网4/大网2'
WHERE grid_model_version IS NULL;

-- 4. 添加索引以提高查询性能
CREATE INDEX idx_grid_line_state ON grid_line(state);
CREATE INDEX idx_grid_line_level ON grid_line(level);

-- 验证修改
SELECT 
    TABLE_NAME, 
    COLUMN_NAME, 
    COLUMN_TYPE, 
    IS_NULLABLE, 
    COLUMN_DEFAULT, 
    COLUMN_COMMENT
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('strategy', 'grid_line')
  AND COLUMN_NAME IN ('grid_model_version', 'grid_summary', 'actual_buy_price', 'actual_sell_price')
ORDER BY TABLE_NAME, ORDINAL_POSITION;
