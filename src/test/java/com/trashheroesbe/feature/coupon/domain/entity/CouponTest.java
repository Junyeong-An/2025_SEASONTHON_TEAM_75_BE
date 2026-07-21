package com.trashheroesbe.feature.coupon.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.trashheroesbe.feature.coupon.domain.type.CouponType;
import com.trashheroesbe.feature.coupon.domain.type.DiscountType;
import com.trashheroesbe.feature.coupon.dto.request.CouponCreateRequest;
import com.trashheroesbe.feature.partner.domain.entity.Partner;
import com.trashheroesbe.fixture.CouponFixture;
import com.trashheroesbe.fixture.PartnerFixture;
import com.trashheroesbe.global.exception.BusinessException;
import com.trashheroesbe.global.response.type.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CouponTest {

    private final Partner partner = PartnerFixture.partner(1L);

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("생성 시 발급 수량 0, 활성 상태로 초기화된다")
        void 생성_초기값() {
            CouponCreateRequest request = new CouponCreateRequest(
                "어스어스 3000원 할인 쿠폰",
                "어스어스에서 3000원 할인받을 수 있는 쿠폰",
                CouponType.OFFLINE,
                3000,
                DiscountType.AMOUNT,
                3000,
                100
            );

            Coupon coupon = Coupon.create(request, partner);

            assertThat(coupon.getIssuedCount()).isZero();
            assertThat(coupon.getIsActive()).isTrue();
            assertThat(coupon.getPartner()).isSameAs(partner);
            assertThat(coupon.getTitle()).isEqualTo(request.title());
            assertThat(coupon.getContent()).isEqualTo(request.content());
            assertThat(coupon.getType()).isEqualTo(request.type());
            assertThat(coupon.getPointCost()).isEqualTo(request.pointCost());
            assertThat(coupon.getDiscountType()).isEqualTo(request.discountType());
            assertThat(coupon.getDiscountValue()).isEqualTo(request.discountValue());
            assertThat(coupon.getTotalStock()).isEqualTo(request.totalStock());
        }
    }

    @Nested
    @DisplayName("issue")
    class Issue {

        @Test
        @DisplayName("발급하면 발급 수량이 1 증가한다")
        void 발급_수량_증가() {
            Coupon coupon = CouponFixture.builder(1L, partner)
                .totalStock(10)
                .issuedCount(3)
                .build();

            coupon.issue();

            assertThat(coupon.getIssuedCount()).isEqualTo(4);
        }

        @Test
        @DisplayName("마지막 남은 한 장까지 발급할 수 있다")
        void 마지막_한장_발급() {
            Coupon coupon = CouponFixture.builder(1L, partner)
                .totalStock(1)
                .issuedCount(0)
                .build();

            coupon.issue();

            assertThat(coupon.getIssuedCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("재고가 모두 소진되면 COUPON_OUT_OF_STOCK 예외가 발생하고 수량은 변하지 않는다")
        void 재고_소진_예외() {
            Coupon coupon = CouponFixture.builder(1L, partner)
                .totalStock(5)
                .issuedCount(5)
                .build();

            assertThatThrownBy(coupon::issue)
                .isInstanceOfSatisfying(BusinessException.class, e ->
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.COUPON_OUT_OF_STOCK));
            assertThat(coupon.getIssuedCount()).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("applyUpdate")
    class ApplyUpdate {

        @Test
        @DisplayName("null 필드는 기존 값을 유지한다")
        void null_필드_유지() {
            Coupon coupon = CouponFixture.coupon(1L, partner);

            coupon.applyUpdate(null, null, null, null, null, null, null, null);

            assertThat(coupon.getTitle()).isEqualTo("어스어스 3000원 할인 쿠폰");
            assertThat(coupon.getContent()).isEqualTo("어스어스에서 3000원 할인받을 수 있는 쿠폰");
            assertThat(coupon.getType()).isEqualTo(CouponType.OFFLINE);
            assertThat(coupon.getPointCost()).isEqualTo(3000);
            assertThat(coupon.getDiscountType()).isEqualTo(DiscountType.AMOUNT);
            assertThat(coupon.getDiscountValue()).isEqualTo(3000);
            assertThat(coupon.getTotalStock()).isEqualTo(10);
            assertThat(coupon.getIsActive()).isTrue();
        }

        @Test
        @DisplayName("빈 문자열 제목과 내용은 무시된다")
        void 빈_문자열_무시() {
            Coupon coupon = CouponFixture.coupon(1L, partner);

            coupon.applyUpdate("  ", "  ", null, null, null, null, null, null);

            assertThat(coupon.getTitle()).isEqualTo("어스어스 3000원 할인 쿠폰");
            assertThat(coupon.getContent()).isEqualTo("어스어스에서 3000원 할인받을 수 있는 쿠폰");
        }

        @Test
        @DisplayName("값이 있는 필드만 반영된다")
        void 부분_수정() {
            Coupon coupon = CouponFixture.coupon(1L, partner);

            coupon.applyUpdate("새 제목", null, null, 500, null, null, null, false);

            assertThat(coupon.getTitle()).isEqualTo("새 제목");
            assertThat(coupon.getPointCost()).isEqualTo(500);
            assertThat(coupon.getIsActive()).isFalse();
            assertThat(coupon.getContent()).isEqualTo("어스어스에서 3000원 할인받을 수 있는 쿠폰");
        }

        @Test
        @DisplayName("재고를 이미 발급된 수량보다 작게 줄이면 예외가 발생한다")
        void 재고_축소_불가() {
            Coupon coupon = CouponFixture.builder(1L, partner)
                .totalStock(10)
                .issuedCount(5)
                .build();

            assertThatThrownBy(() ->
                coupon.applyUpdate(null, null, null, null, null, null, 3, null))
                .isInstanceOf(IllegalArgumentException.class);
            assertThat(coupon.getTotalStock()).isEqualTo(10);
        }

        @Test
        @DisplayName("재고를 발급된 수량과 같게 줄이는 것은 허용된다")
        void 재고_동일_축소_허용() {
            Coupon coupon = CouponFixture.builder(1L, partner)
                .totalStock(10)
                .issuedCount(5)
                .build();

            coupon.applyUpdate(null, null, null, null, null, null, 5, null);

            assertThat(coupon.getTotalStock()).isEqualTo(5);
        }
    }
}
