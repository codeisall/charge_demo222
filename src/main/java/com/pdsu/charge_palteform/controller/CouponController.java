package com.pdsu.charge_palteform.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "优惠券管理", description = "优惠券相关接口")
@RestController
@RequestMapping("/api/coupon")
@RequiredArgsConstructor
public class CouponController {

}
