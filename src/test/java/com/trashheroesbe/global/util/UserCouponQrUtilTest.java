package com.trashheroesbe.global.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserCouponQrUtilTest {

    @Test
    @DisplayName("QR 페이로드는 baseUrl에 userCouponId와 qrToken 쿼리를 붙인다")
    void 페이로드_형식() {
        String payload = UserCouponQrUtil.buildPayload(
            "https://trash-heroes.store/qr", 100L, "token-123");

        assertThat(payload)
            .isEqualTo("https://trash-heroes.store/qr?userCouponId=100&qrToken=token-123");
    }

    @Test
    @DisplayName("QR 저장 키는 user-coupon/{id}/qr.png 형식이다")
    void 저장_키_형식() {
        assertThat(UserCouponQrUtil.buildKey(100L)).isEqualTo("user-coupon/100/qr.png");
    }
}
