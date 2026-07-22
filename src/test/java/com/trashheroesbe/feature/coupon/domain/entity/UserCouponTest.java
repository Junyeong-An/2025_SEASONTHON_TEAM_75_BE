package com.trashheroesbe.feature.coupon.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.trashheroesbe.feature.coupon.domain.type.CouponStatus;
import com.trashheroesbe.feature.partner.domain.entity.Partner;
import com.trashheroesbe.feature.user.domain.entity.User;
import com.trashheroesbe.fixture.CouponFixture;
import com.trashheroesbe.fixture.PartnerFixture;
import com.trashheroesbe.fixture.UserFixture;
import com.trashheroesbe.global.exception.BusinessException;
import com.trashheroesbe.global.response.type.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class UserCouponTest {

    private final Partner partner = PartnerFixture.partner(1L);
    private final User user = UserFixture.user(1L);
    private final Coupon coupon = CouponFixture.coupon(1L, partner);

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("생성 시 사용 가능 상태로 초기화된다")
        void 생성_초기값() {
            // when
            UserCoupon userCoupon = UserCoupon.create(user, coupon);

            // then
            assertThat(userCoupon.getStatus()).isEqualTo(CouponStatus.AVAILABLE);
            assertThat(userCoupon.getUsedAt()).isNull();
            assertThat(userCoupon.getUser()).isSameAs(user);
            assertThat(userCoupon.getCoupon()).isSameAs(coupon);
        }
    }

    @Nested
    @DisplayName("useCoupon")
    class UseCoupon {

        @Test
        @DisplayName("사용하면 사용 완료 상태가 되고 사용 시각이 기록된다")
        void 사용_상태_전이() {
            // given
            UserCoupon userCoupon = UserCoupon.create(user, coupon);

            // when
            userCoupon.useCoupon();

            // then
            assertThat(userCoupon.getStatus()).isEqualTo(CouponStatus.USED);
            assertThat(userCoupon.getUsedAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("attachQr")
    class AttachQr {

        @Test
        @DisplayName("QR 토큰과 이미지 URL을 저장한다")
        void QR_저장() {
            // given
            UserCoupon userCoupon = UserCoupon.create(user, coupon);

            // when
            userCoupon.attachQr("qr-token", "https://storage.test/qr.png");

            // then
            assertThat(userCoupon.getQrToken()).isEqualTo("qr-token");
            assertThat(userCoupon.getQrImageUrl()).isEqualTo("https://storage.test/qr.png");
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", "  "})
        @DisplayName("QR 토큰이 비어 있으면 VALIDATION_FAILED 예외가 발생한다")
        void 토큰_검증(String invalidToken) {
            // given
            UserCoupon userCoupon = UserCoupon.create(user, coupon);

            // when & then
            assertThatThrownBy(() ->
                userCoupon.attachQr(invalidToken, "https://storage.test/qr.png"))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", "  "})
        @DisplayName("QR 이미지 URL이 비어 있으면 VALIDATION_FAILED 예외가 발생한다")
        void URL_검증(String invalidUrl) {
            // given
            UserCoupon userCoupon = UserCoupon.create(user, coupon);

            // when & then
            assertThatThrownBy(() -> userCoupon.attachQr("qr-token", invalidUrl))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        }
    }
}
