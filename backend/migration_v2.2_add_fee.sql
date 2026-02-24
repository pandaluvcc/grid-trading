-- 网格交易系统 v2.2 迁移脚本
-- 为成交记录增加手续费字段

-- 添加 fee 字段到 trade_record 表
ALTER TABLE trade_record ADD COLUMN fee DECIMAL(20, 8) DEFAULT NULL;

-- 添加注释
-- fee: 用户手动录入的手续费（买入/卖出时交易所收取的费用）
