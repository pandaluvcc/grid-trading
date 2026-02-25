package com.gridtrading.service.grid;

import com.gridtrading.domain.GridType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GridPlanGeneratorTest {

    @Test
    void generatesFixed19Lines() {
        GridPlanGenerator generator = new GridPlanGenerator();
        List<GridPlanGenerator.GridPlanItem> items = generator.generate(
                new BigDecimal("1.500"),
                new BigDecimal("1500.00")
        );

        assertEquals(19, items.size());
        assertEquals(1, items.get(0).getLevel());
        assertEquals(GridType.SMALL, items.get(0).getGridType());
        assertNotNull(items.get(0).getBuyPrice());
        assertNotNull(items.get(0).getSellPrice());
        assertEquals(19, items.get(18).getLevel());
    }
}

