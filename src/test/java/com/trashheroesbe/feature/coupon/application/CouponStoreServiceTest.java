package com.trashheroesbe.feature.coupon.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.trashheroesbe.feature.coupon.domain.entity.Coupon;
import com.trashheroesbe.feature.coupon.domain.entity.UserCoupon;
import com.trashheroesbe.feature.coupon.domain.type.CouponStatus;
import com.trashheroesbe.feature.coupon.dto.request.CouponPurchaseRequest;
import com.trashheroesbe.feature.coupon.dto.response.PurchaseUserCouponResponse;
import com.trashheroesbe.feature.coupon.infrastructure.CouponRepository;
import com.trashheroesbe.feature.coupon.infrastructure.UserCouponRepository;
import com.trashheroesbe.feature.partner.domain.entity.Partner;
import com.trashheroesbe.feature.point.application.PointService;
import com.trashheroesbe.feature.point.domain.type.PointReason;
import com.trashheroesbe.feature.user.domain.entity.User;
import com.trashheroesbe.fixture.CouponFixture;
import com.trashheroesbe.fixture.PartnerFixture;
import com.trashheroesbe.fixture.UserFixture;
import com.trashheroesbe.global.exception.BusinessException;
import com.trashheroesbe.global.qrcode.QrCodeGenerator;
import com.trashheroesbe.global.response.type.ErrorCode;
import com.trashheroesbe.infrastructure.port.s3.FileStoragePort;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CouponStoreServiceTest {

    private static final String QR_BASE_URL = "https://test.trash-heroes.store/api/v1/my/coupons/qr";

    @Mock
    private PointService pointService;

    @Mock
    private CouponRepository couponRepository;

    @Mock
    private UserCouponRepository userCouponRepository;

    @Mock
    private QrCodeGenerator qrCodeGenerator;

    @Mock
    private FileStoragePort fileStoragePort;

    @InjectMocks
    private CouponStoreService couponStoreService;

    private final Partner partner = PartnerFixture.partner(1L);
    private final User user = UserFixture.user(5L);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(couponStoreService, "userCouponQrBaseUrl", QR_BASE_URL);
    }

    @Test
    @DisplayName("구매 성공 시 재고 차감, 포인트 사용, QR 발급까지 수행한다")
    void 구매_성공() {
        Coupon coupon = CouponFixture.builder(10L, partner)
            .totalStock(10)
            .issuedCount(3)
            .pointCost(3000)
            .build();
        given(couponRepository.findByIdFetchPartner(10L)).willReturn(Optional.of(coupon));

        UserCoupon savedUserCoupon = UserCoupon.builder()
            .id(100L)
            .user(user)
            .coupon(coupon)
            .status(CouponStatus.AVAILABLE)
            .build();
        given(userCouponRepository.save(any(UserCoupon.class))).willReturn(savedUserCoupon);
        given(qrCodeGenerator.generatePngBytes(anyString(), eq(300)))
            .willReturn(new byte[]{1, 2, 3});
        given(fileStoragePort.uploadFile(eq("user-coupon/100/qr.png"), eq("image/png"), any()))
            .willReturn("https://storage.test/user-coupon/100/qr.png");

        PurchaseUserCouponResponse response =
            couponStoreService.purchaseCoupon(new CouponPurchaseRequest(10L), user);

        assertThat(coupon.getIssuedCount()).isEqualTo(4);
        verify(pointService).usePoint(user.getId(), 3000, PointReason.COUPON_PURCHASE, 10L);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(qrCodeGenerator).generatePngBytes(payloadCaptor.capture(), eq(300));
        assertThat(payloadCaptor.getValue())
            .startsWith(QR_BASE_URL + "?userCouponId=100&qrToken=");

        assertThat(savedUserCoupon.getQrToken()).isNotBlank();
        assertThat(savedUserCoupon.getQrImageUrl())
            .isEqualTo("https://storage.test/user-coupon/100/qr.png");

        assertThat(response.userCouponId()).isEqualTo(100L);
        assertThat(response.couponId()).isEqualTo(10L);
        assertThat(response.pointsUsed()).isEqualTo(3000);
        assertThat(response.qrImageUrl())
            .isEqualTo("https://storage.test/user-coupon/100/qr.png");
    }

    @Test
    @DisplayName("존재하지 않는 쿠폰이면 COUPON_NOT_FOUND 예외가 발생한다")
    void 쿠폰_없음() {
        given(couponRepository.findByIdFetchPartner(anyLong())).willReturn(Optional.empty());

        assertThatThrownBy(() ->
            couponStoreService.purchaseCoupon(new CouponPurchaseRequest(99L), user))
            .isInstanceOfSatisfying(BusinessException.class, e ->
                assertThat(e.getErrorCode()).isEqualTo(ErrorCode.COUPON_NOT_FOUND));
    }

    @Test
    @DisplayName("비활성 쿠폰이면 COUPON_NOT_AVAILABLE 예외가 발생하고 포인트는 차감되지 않는다")
    void 비활성_쿠폰() {
        Coupon coupon = CouponFixture.builder(10L, partner)
            .isActive(false)
            .build();
        given(couponRepository.findByIdFetchPartner(10L)).willReturn(Optional.of(coupon));

        assertThatThrownBy(() ->
            couponStoreService.purchaseCoupon(new CouponPurchaseRequest(10L), user))
            .isInstanceOfSatisfying(BusinessException.class, e ->
                assertThat(e.getErrorCode()).isEqualTo(ErrorCode.COUPON_NOT_AVAILABLE));
        assertThat(coupon.getIssuedCount()).isZero();
        verify(pointService, never()).usePoint(anyLong(), anyInt(), any(), anyLong());
    }

    @Test
    @DisplayName("재고가 소진된 쿠폰이면 COUPON_OUT_OF_STOCK 예외가 발생하고 포인트는 차감되지 않는다")
    void 재고_소진() {
        Coupon coupon = CouponFixture.builder(10L, partner)
            .totalStock(5)
            .issuedCount(5)
            .build();
        given(couponRepository.findByIdFetchPartner(10L)).willReturn(Optional.of(coupon));

        assertThatThrownBy(() ->
            couponStoreService.purchaseCoupon(new CouponPurchaseRequest(10L), user))
            .isInstanceOfSatisfying(BusinessException.class, e ->
                assertThat(e.getErrorCode()).isEqualTo(ErrorCode.COUPON_OUT_OF_STOCK));
        verify(pointService, never()).usePoint(anyLong(), anyInt(), any(), anyLong());
    }

    @Test
    @DisplayName("포인트가 부족하면 예외가 전파되고 보유 쿠폰은 저장되지 않는다")
    void 포인트_부족() {
        Coupon coupon = CouponFixture.builder(10L, partner)
            .pointCost(3000)
            .build();
        given(couponRepository.findByIdFetchPartner(10L)).willReturn(Optional.of(coupon));
        willThrow(new BusinessException(ErrorCode.INSUFFICIENT_POINTS))
            .given(pointService)
            .usePoint(user.getId(), 3000, PointReason.COUPON_PURCHASE, 10L);

        assertThatThrownBy(() ->
            couponStoreService.purchaseCoupon(new CouponPurchaseRequest(10L), user))
            .isInstanceOfSatisfying(BusinessException.class, e ->
                assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INSUFFICIENT_POINTS));
        verify(userCouponRepository, never()).save(any(UserCoupon.class));
    }
}
