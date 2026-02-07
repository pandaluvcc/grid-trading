-- 检查第19条网格的卖出价
SELECT 
    s.id AS strategy_id,
    s.symbol,
    s.base_price,
    gl.level,
    gl.grid_type,
    gl.buy_price,
    gl.sell_price,
    CASE 
        WHEN gl.grid_type = 'LARGE' AND gl.sell_price != s.base_price THEN 'ERROR: 大网卖出价应该等于基准价'
        ELSE 'OK'
    END AS validation
FROM strategy s
JOIN grid_line gl ON gl.strategy_id = s.id
WHERE gl.level IN (10, 19)  -- 检查两个大网
ORDER BY s.id, gl.level;

-- 检查所有大网的卖出价
SELECT 
    s.id,
    s.base_price,
    gl.level,
    gl.grid_type,
    gl.sell_price,
    (s.base_price - gl.sell_price) AS price_diff
FROM strategy s
JOIN grid_line gl ON gl.strategy_id = s.id
WHERE gl.grid_type = 'LARGE'
ORDER BY s.id, gl.level;
