package com.ldfd.seckill.controller;

import com.ldfd.seckill.dto.ApiResponse;
import com.ldfd.seckill.dto.SeckillSubmitResult;
import com.ldfd.seckill.service.SeckillService;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api")
public class SeckillController {

    private final SeckillService seckillService;

    public SeckillController(SeckillService seckillService) {
        this.seckillService = seckillService;
    }

    @PostMapping("/seckill/{goodsId}")
    public ApiResponse<SeckillSubmitResult> submit(
            @PathVariable @Min(1) Long goodsId,
            @RequestParam @Min(1) Long userId) {
        SeckillSubmitResult result = seckillService.submit(goodsId, userId);
        if (!result.accepted()) {
            return ApiResponse.fail(result.message());
        }
        return ApiResponse.ok("accepted", result);
    }

    @PostMapping("/admin/goods/{goodsId}/stock/{stock}")
    public ApiResponse<String> initStock(
            @PathVariable @Min(1) Long goodsId,
            @PathVariable @Min(1) Integer stock) {
        seckillService.initGoodsStock(goodsId, stock);
        return ApiResponse.ok("stock initialized", "goodsId=" + goodsId + ", stock=" + stock);
    }
}

