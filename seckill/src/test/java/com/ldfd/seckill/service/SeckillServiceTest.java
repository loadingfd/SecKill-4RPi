package com.ldfd.seckill.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ldfd.seckill.domain.SeckillGoods;
import com.ldfd.seckill.dto.SeckillSubmitResult;
import com.ldfd.seckill.mapper.SeckillGoodsMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SeckillServiceTest {

    @Mock
    private RedisStockService redisStockService;

    @Mock
    private OrderMessagePublisherService orderMessagePublisherService;

    @Mock
    private SeckillGoodsMapper seckillGoodsMapper;

    private SeckillService seckillService;

    @BeforeEach
    void setUp() {
        seckillService = new SeckillService(redisStockService, orderMessagePublisherService, seckillGoodsMapper);
    }

    @Test
    void submitShouldReturnLimitExceededWhenUserReachedPerUserLimit() {
        when(redisStockService.preDeduct(1001L, 2002L)).thenReturn(-2L);

        SeckillSubmitResult result = seckillService.submit(1001L, 2002L);

        assertFalse(result.accepted());
        assertEquals("exceed per-user limit", result.message());
        verify(orderMessagePublisherService, never()).publish(any());
    }

    @Test
    void initGoodsStockShouldRejectExistingGoodsActivity() {
        when(seckillGoodsMapper.existsById(1001L)).thenReturn(true);

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> seckillService.initGoodsStock(1001L, 20, 3));

        assertEquals("activity already initialized; only new goodsId can use new limit", ex.getMessage());
        verify(redisStockService, never()).initStock(anyLong(), anyInt(), anyInt());
        verify(seckillGoodsMapper, never()).insert(any());
    }

    @Test
    void initGoodsStockShouldPersistLimitForNewActivity() {
        when(seckillGoodsMapper.existsById(1001L)).thenReturn(false);

        seckillService.initGoodsStock(1001L, 20, 3);

        ArgumentCaptor<SeckillGoods> goodsCaptor = ArgumentCaptor.forClass(SeckillGoods.class);
        verify(seckillGoodsMapper).insert(goodsCaptor.capture());
        SeckillGoods saved = goodsCaptor.getValue();
        assertEquals(1001L, saved.getGoodsId());
        assertEquals(20, saved.getStock());
        assertEquals(3, saved.getPerUserLimit());
        verify(redisStockService).initStock(1001L, 20, 3);
    }
}
