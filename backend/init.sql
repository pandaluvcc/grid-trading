CREATE DATABASE IF NOT EXISTS grid_trading
DEFAULT CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

USE grid_trading;

-- ========================================
-- v3.0 网格计划重构迁移脚本
-- 执行日期：2026-02-07
-- ========================================

-- 1. Strategy 表添加价差字段
-- ALTER TABLE strategy ADD COLUMN small_gap DECIMAL(20,8) AFTER amount_per_grid;
-- ALTER TABLE strategy ADD COLUMN medium_gap DECIMAL(20,8) AFTER small_gap;
-- ALTER TABLE strategy ADD COLUMN large_gap DECIMAL(20,8) AFTER medium_gap;

-- 2. GridLine 表添加计算字段
-- ALTER TABLE grid_line ADD COLUMN buy_amount DECIMAL(20,2) NOT NULL DEFAULT 0 AFTER level;
-- ALTER TABLE grid_line ADD COLUMN buy_quantity DECIMAL(20,8) NOT NULL DEFAULT 0 AFTER buy_amount;
-- ALTER TABLE grid_line ADD COLUMN sell_amount DECIMAL(20,2) NOT NULL DEFAULT 0 AFTER buy_quantity;
-- ALTER TABLE grid_line ADD COLUMN profit DECIMAL(20,2) NOT NULL DEFAULT 0 AFTER sell_amount;
-- ALTER TABLE grid_line ADD COLUMN profit_rate DECIMAL(10,6) NOT NULL DEFAULT 0 AFTER profit;

-- 3. 数据迁移（如果需要）
-- 为已有 GridLine 计算并填充新字段
-- UPDATE grid_line gl
-- JOIN strategy s ON gl.strategy_id = s.id
-- SET
--   gl.buy_amount = s.amount_per_grid,
--   gl.buy_quantity = s.amount_per_grid / gl.buy_price,
--   gl.sell_amount = (s.amount_per_grid / gl.buy_price) * gl.sell_price,
--   gl.profit = ((s.amount_per_grid / gl.buy_price) * gl.sell_price) - s.amount_per_grid,
--   gl.profit_rate = (((s.amount_per_grid / gl.buy_price) * gl.sell_price) - s.amount_per_grid) / s.amount_per_grid
-- WHERE gl.buy_amount = 0;

-- ========================================
-- 注意事项
-- ========================================
-- 1. 以上脚本仅用于已有数据库升级
-- 2. 新建数据库会由 JPA 自动创建表结构
-- 3. 建议先在测试环境验证
-- 4. 执行前请备份数据库
