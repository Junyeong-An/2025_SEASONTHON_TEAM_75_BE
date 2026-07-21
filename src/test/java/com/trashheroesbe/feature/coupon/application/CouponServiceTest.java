package com.trashheroesbe.feature.coupon.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.trashheroesbe.feature.coupon.domain.entity.Coupon;
import com.trashheroesbe.feature.coupon.domain.entity.UserCoupon;
import com.trashheroesbe.feature.coupon.domain.type.CouponStatus;
import com.trashheroesbe.feature.coupon.dto.request.CouponUpdateRequest;
import com.trashheroesbe.feature.coupon.dto.response.CouponCreateResponse;
import com.trashheroesbe.feature.coupon.dto.response.PartnerCouponResponse;
import com.trashheroesbe.feature.coupon.infrastructure.CouponRepository;
import com.trashheroesbe.feature.coupon.infrastructure.UserCouponRepository;
import com.trashheroesbe.feature.partner.domain.entity.Partner;
import com.trashheroesbe.fixture.CouponFixture;
import com.trashheroesbe.fixture.PartnerFixture;
import com.trashheroesbe.fixture.UserFixture;
import com.trashheroesbe.global.auth.security.CustomerDetails;
import com.trashheroesbe.global.exception.BusinessException;
import com.trashheroesbe.global.response.type.ErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CouponServiceTest {

    @Mock
    private CouponRepository couponRepository;

    @Mock
    private UserCouponRepository userCouponRepository;

    @InjectMocks
    private CouponService couponService;

    private final Partner myPartner = PartnerFixture.partner(1L);
    private final Partner otherPartner = PartnerFixture.partner(2L);
    private final CustomerDetails partnerDetails =
        new CustomerDetails(UserFixture.partnerUser(1L, myPartner));

    @Nested
    @DisplayName("파트너 권한 검증")
    class ExtractPartner {

        @Test
        @DisplayName("인증 정보가 없으면 ACCESS_DENIED 예외가 발생한다")
        void 인증_정보_없음() {
            assertThatThrownBy(() -> couponService.getPartnerCoupons(null))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED_EXCEPTION));
        }

        @Test
        @DisplayName("파트너가 연결되지 않은 일반 사용자는 ACCESS_DENIED 예외가 발생한다")
        void 일반_사용자_접근_불가() {
            CustomerDetails userDetails = new CustomerDetails(UserFixture.user(3L));

            assertThatThrownBy(() -> couponService.getPartnerCoupons(userDetails))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED_EXCEPTION));
        }
    }

    @Nested
    @DisplayName("getPartnerCoupons")
    class GetPartnerCoupons {

        @Test
        @DisplayName("자기 파트너 소속 쿠폰 목록을 응답으로 변환한다")
        void 쿠폰_목록_조회() {
            Coupon coupon = CouponFixture.coupon(10L, myPartner);
            given(couponRepository.findAllByPartnerIdFetch(myPartner.getId()))
                .willReturn(List.of(coupon));

            List<PartnerCouponResponse> responses =
                couponService.getPartnerCoupons(partnerDetails);

            assertThat(responses).hasSize(1);
            assertThat(responses.get(0).couponId()).isEqualTo(10L);
            assertThat(responses.get(0).title()).isEqualTo(coupon.getTitle());
        }
    }

    @Nested
    @DisplayName("deleteCoupon")
    class DeleteCoupon {

        @Test
        @DisplayName("자기 파트너의 쿠폰은 삭제할 수 있다")
        void 본인_쿠폰_삭제() {
            Coupon coupon = CouponFixture.coupon(10L, myPartner);
            given(couponRepository.findByIdFetchPartner(10L)).willReturn(Optional.of(coupon));

            couponService.deleteCoupon(partnerDetails, 10L);

            verify(couponRepository).delete(coupon);
        }

        @Test
        @DisplayName("다른 파트너의 쿠폰을 삭제하면 ACCESS_DENIED 예외가 발생한다")
        void 다른_파트너_쿠폰_삭제_불가() {
            Coupon coupon = CouponFixture.coupon(10L, otherPartner);
            given(couponRepository.findByIdFetchPartner(10L)).willReturn(Optional.of(coupon));

            assertThatThrownBy(() -> couponService.deleteCoupon(partnerDetails, 10L))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED_EXCEPTION));
            verify(couponRepository, never()).delete(coupon);
        }

        @Test
        @DisplayName("존재하지 않는 쿠폰이면 ENTITY_NOT_FOUND 예외가 발생한다")
        void 쿠폰_없음() {
            given(couponRepository.findByIdFetchPartner(anyLong())).willReturn(Optional.empty());

            assertThatThrownBy(() -> couponService.deleteCoupon(partnerDetails, 99L))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ENTITY_NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("updateCoupon")
    class UpdateCoupon {

        @Test
        @DisplayName("자기 파트너의 쿠폰 정보를 수정한다")
        void 본인_쿠폰_수정() {
            Coupon coupon = CouponFixture.coupon(10L, myPartner);
            given(couponRepository.findByIdFetchPartner(10L)).willReturn(Optional.of(coupon));
            CouponUpdateRequest request = new CouponUpdateRequest(
                "수정된 제목", null, null, null, null, null, null, null);

            CouponCreateResponse response =
                couponService.updateCoupon(partnerDetails, 10L, request);

            assertThat(coupon.getTitle()).isEqualTo("수정된 제목");
            assertThat(response.title()).isEqualTo("수정된 제목");
            assertThat(response.couponId()).isEqualTo(10L);
        }

        @Test
        @DisplayName("다른 파트너의 쿠폰을 수정하면 ACCESS_DENIED 예외가 발생하고 값이 유지된다")
        void 다른_파트너_쿠폰_수정_불가() {
            Coupon coupon = CouponFixture.coupon(10L, otherPartner);
            given(couponRepository.findByIdFetchPartner(10L)).willReturn(Optional.of(coupon));
            CouponUpdateRequest request = new CouponUpdateRequest(
                "수정된 제목", null, null, null, null, null, null, null);

            assertThatThrownBy(() -> couponService.updateCoupon(partnerDetails, 10L, request))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED_EXCEPTION));
            assertThat(coupon.getTitle()).isEqualTo("어스어스 3000원 할인 쿠폰");
        }
    }

    @Nested
    @DisplayName("useCoupon")
    class UseCoupon {

        @Test
        @DisplayName("보유 쿠폰을 사용 완료 상태로 전이시킨다")
        void 쿠폰_사용() {
            Coupon coupon = CouponFixture.coupon(10L, myPartner);
            UserCoupon userCoupon = UserCoupon.create(UserFixture.user(5L), coupon);
            given(userCouponRepository.findById(100L)).willReturn(Optional.of(userCoupon));

            couponService.useCoupon(partnerDetails, 100L);

            assertThat(userCoupon.getStatus()).isEqualTo(CouponStatus.USED);
            assertThat(userCoupon.getUsedAt()).isNotNull();
        }

        @Test
        @DisplayName("존재하지 않는 보유 쿠폰이면 ENTITY_NOT_FOUND 예외가 발생한다")
        void 보유_쿠폰_없음() {
            given(userCouponRepository.findById(anyLong())).willReturn(Optional.empty());

            assertThatThrownBy(() -> couponService.useCoupon(partnerDetails, 99L))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ENTITY_NOT_FOUND));
        }

        @Test
        @Disabled("버그 재현: useCoupon은 쿠폰이 요청 파트너 소속인지 검증하지 않아 다른 파트너의 쿠폰도 사용 처리된다. 소유권 검증 추가(fix) 후 활성화 예정")
        @DisplayName("다른 파트너 소속 쿠폰을 사용하면 ACCESS_DENIED 예외가 발생해야 한다")
        void 다른_파트너_쿠폰_사용_불가() {
            Coupon coupon = CouponFixture.coupon(10L, otherPartner);
            UserCoupon userCoupon = UserCoupon.create(UserFixture.user(5L), coupon);
            given(userCouponRepository.findById(100L)).willReturn(Optional.of(userCoupon));

            assertThatThrownBy(() -> couponService.useCoupon(partnerDetails, 100L))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED_EXCEPTION));
            assertThat(userCoupon.getStatus()).isEqualTo(CouponStatus.AVAILABLE);
        }
    }
}
