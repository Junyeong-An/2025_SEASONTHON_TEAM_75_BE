package com.trashheroesbe.fixture;

import com.trashheroesbe.feature.coupon.domain.entity.Coupon;
import com.trashheroesbe.feature.coupon.domain.type.CouponType;
import com.trashheroesbe.feature.coupon.domain.type.DiscountType;
import com.trashheroesbe.feature.partner.domain.entity.Partner;

public class CouponFixture {

    public static Coupon.CouponBuilder builder(Long id, Partner partner) {
        return Coupon.builder()
            .id(id)
            .partner(partner)
            .title("어스어스 3000원 할인 쿠폰")
            .content("어스어스에서 3000원 할인받을 수 있는 쿠폰")
            .type(CouponType.OFFLINE)
            .pointCost(3000)
            .discountType(DiscountType.AMOUNT)
            .discountValue(3000)
            .totalStock(10)
            .issuedCount(0)
            .isActive(true)
            .version(0L);
    }

    public static Coupon coupon(Long id, Partner partner) {
        return builder(id, partner).build();
    }
}
