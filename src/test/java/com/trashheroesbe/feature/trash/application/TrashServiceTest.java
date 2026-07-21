package com.trashheroesbe.feature.trash.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.trashheroesbe.feature.disposal.infrastructure.DisposalRepository;
import com.trashheroesbe.feature.badge.application.BadgeService;
import com.trashheroesbe.feature.point.application.PointService;
import com.trashheroesbe.feature.search.application.SearchLogService;
import com.trashheroesbe.feature.trash.domain.entity.Part;
import com.trashheroesbe.feature.trash.domain.entity.Trash;
import com.trashheroesbe.feature.trash.domain.entity.TrashDescription;
import com.trashheroesbe.feature.trash.domain.entity.TrashItem;
import com.trashheroesbe.feature.trash.domain.entity.TrashType;
import com.trashheroesbe.feature.trash.domain.type.ItemType;
import com.trashheroesbe.feature.trash.domain.type.Type;
import com.trashheroesbe.feature.trash.dto.response.TrashItemResponse;
import com.trashheroesbe.feature.trash.dto.response.TrashResultResponse;
import com.trashheroesbe.feature.trash.infrastructure.PartRepository;
import com.trashheroesbe.feature.trash.infrastructure.TrashDescriptionRepository;
import com.trashheroesbe.feature.trash.infrastructure.TrashItemRepository;
import com.trashheroesbe.feature.trash.infrastructure.TrashPartRepository;
import com.trashheroesbe.feature.trash.infrastructure.TrashRepository;
import com.trashheroesbe.feature.trash.infrastructure.TrashTypeRepository;
import com.trashheroesbe.feature.user.domain.entity.User;
import com.trashheroesbe.feature.user.infrastructure.UserDistrictRepository;
import com.trashheroesbe.fixture.UserFixture;
import com.trashheroesbe.global.exception.BusinessException;
import com.trashheroesbe.global.response.type.ErrorCode;
import com.trashheroesbe.infrastructure.port.gpt.ChatAIClientPort;
import com.trashheroesbe.infrastructure.port.s3.FileStoragePort;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

@ExtendWith(MockitoExtension.class)
class TrashServiceTest {

    @Mock
    private TrashRepository trashRepository;

    @Mock
    private TrashTypeRepository trashTypeRepository;

    @Mock
    private TrashItemRepository trashItemRepository;

    @Mock
    private TrashDescriptionRepository trashDescriptionRepository;

    @Mock
    private DisposalRepository disposalRepository;

    @Mock
    private UserDistrictRepository userDistrictRepository;

    @Mock
    private PartRepository partRepository;

    @Mock
    private TrashPartRepository trashPartRepository;

    @Mock
    private PlatformTransactionManager txManager;

    @Mock
    private SearchLogService searchLogService;

    @Mock
    private BadgeService badgeService;

    @Mock
    private PointService pointService;

    @Mock
    private FileStoragePort fileStoragePort;

    @Mock
    private ChatAIClientPort chatGPTClientPort;

    @InjectMocks
    private TrashService trashService;

    private final User owner = UserFixture.user(1L);
    private final User otherUser = UserFixture.user(2L);

    private TrashType petType() {
        return TrashType.builder().id(1L).type(Type.PET).build();
    }

    private Trash trashOf(User user, TrashType type, TrashItem item) {
        return Trash.builder()
            .id(1L)
            .user(user)
            .imageUrl("https://storage.test/trash/photo.jpg")
            .name("페트병")
            .trashType(type)
            .trashItem(item)
            .build();
    }

    @Nested
    @DisplayName("getTrash")
    class GetTrash {

        @Test
        @DisplayName("존재하지 않는 쓰레기면 NOT_EXISTS_TRASH_ITEM 예외가 발생한다")
        void 쓰레기_없음() {
            // given
            given(trashRepository.findById(anyLong())).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> trashService.getTrash(99L))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.NOT_EXISTS_TRASH_ITEM));
        }

        @Test
        @DisplayName("타입 정보, 배출 가이드, 부품 카드를 포함한 결과를 반환한다")
        void 상세_조회() {
            // given
            TrashType type = petType();
            TrashItem item = TrashItem.builder()
                .id(5L).name("PET(투명 페트병)").itemType(ItemType.NORMAL).trashType(type)
                .build();
            Trash trash = trashOf(owner, type, item);
            given(trashRepository.findById(1L)).willReturn(Optional.of(trash));
            given(trashDescriptionRepository.findByTrashType(type)).willReturn(Optional.of(
                TrashDescription.builder()
                    .trashType(type)
                    .methodDetail("STEP 1: 내용물을 비워요.\nSTEP 2: 라벨을 제거해요.")
                    .cautionNote("주의: 유색 페트병은 분리하지 않아요.")
                    .build()));
            given(trashPartRepository.findPartsByTrashId(1L)).willReturn(List.of(
                Part.builder().id(7L).name("뚜껑").trashType(
                    TrashType.builder().id(2L).type(Type.PLASTIC).build()).build()));

            // when
            TrashResultResponse response = trashService.getTrash(1L);

            // then
            assertThat(response.id()).isEqualTo(1L);
            assertThat(response.itemName()).isEqualTo("PET(투명 페트병)");
            assertThat(response.typeCode()).isEqualTo(Type.PET.getTypeCode());
            assertThat(response.typeName()).isEqualTo(Type.PET.getNameKo());
            assertThat(response.guideSteps()).containsExactly(
                "STEP 1: 내용물을 비워요.", "STEP 2: 라벨을 제거해요.");
            assertThat(response.cautionNote()).isEqualTo("주의: 유색 페트병은 분리하지 않아요.");
            assertThat(response.parts()).hasSize(1);
            assertThat(response.location()).isNull();
            assertThat(response.days()).isEmpty();
        }
    }

    @Nested
    @DisplayName("getTrashByUser")
    class GetTrashByUser {

        @Test
        @DisplayName("사용자의 쓰레기 목록을 응답으로 변환한다")
        void 목록_조회() {
            // given
            TrashType type = petType();
            Trash first = Trash.builder().id(2L).user(owner).name("페트병").trashType(type).build();
            Trash second = Trash.builder().id(1L).user(owner).name("캔").trashType(type).build();
            given(trashRepository.findByUserOrderByCreatedAtDesc(owner))
                .willReturn(List.of(first, second));

            // when
            List<TrashResultResponse> responses = trashService.getTrashByUser(owner);

            // then
            assertThat(responses).hasSize(2);
            assertThat(responses.get(0).id()).isEqualTo(2L);
            assertThat(responses.get(1).id()).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("getTrashItemsByTrashId")
    class GetTrashItems {

        @Test
        @DisplayName("현재 선택된 품목은 제외하고 CAUTION 품목을 먼저 정렬해 반환한다")
        void 품목_목록_조회() {
            // given
            TrashType type = petType();
            TrashItem current = TrashItem.builder()
                .id(5L).name("현재 품목").itemType(ItemType.NORMAL).trashType(type).build();
            TrashItem normal = TrashItem.builder()
                .id(6L).name("일반 품목").itemType(ItemType.NORMAL).trashType(type).build();
            TrashItem caution = TrashItem.builder()
                .id(7L).name("주의 품목").itemType(ItemType.CAUTION).trashType(type).build();
            Trash trash = trashOf(owner, type, current);
            given(trashRepository.findById(1L)).willReturn(Optional.of(trash));
            given(trashItemRepository.findByTrashTypeId(1L))
                .willReturn(List.of(current, normal, caution));

            // when
            List<TrashItemResponse> responses = trashService.getTrashItemsByTrashId(1L);

            // then
            assertThat(responses).hasSize(2);
            assertThat(responses.get(0).trashItemId()).isEqualTo(7L);
            assertThat(responses.get(1).trashItemId()).isEqualTo(6L);
        }

        @Test
        @DisplayName("분석된 타입이 없으면 빈 목록을 반환한다")
        void 타입_없음() {
            // given
            Trash trash = trashOf(owner, null, null);
            given(trashRepository.findById(1L)).willReturn(Optional.of(trash));

            // when
            List<TrashItemResponse> responses = trashService.getTrashItemsByTrashId(1L);

            // then
            assertThat(responses).isEmpty();
        }
    }

    @Nested
    @DisplayName("changeTrashItem")
    class ChangeTrashItem {

        @Test
        @DisplayName("다른 사용자의 쓰레기 품목은 변경할 수 없다")
        void 다른_사용자_변경_불가() {
            // given
            Trash trash = trashOf(owner, petType(), null);
            given(trashRepository.findById(1L)).willReturn(Optional.of(trash));

            // when & then
            assertThatThrownBy(() -> trashService.changeTrashItem(1L, 5L, otherUser))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.NOT_EXISTS_TRASH_ITEM));
            assertThat(trash.getTrashItem()).isNull();
        }

        @Test
        @DisplayName("쓰레기 타입과 품목 타입이 다르면 변경할 수 없다")
        void 타입_불일치_변경_불가() {
            // given
            TrashType petType = petType();
            TrashType plasticType = TrashType.builder().id(2L).type(Type.PLASTIC).build();
            Trash trash = trashOf(owner, petType, null);
            TrashItem item = TrashItem.builder()
                .id(5L).name("플라스틱 품목").itemType(ItemType.NORMAL).trashType(plasticType)
                .build();
            given(trashRepository.findById(1L)).willReturn(Optional.of(trash));
            given(trashItemRepository.findById(5L)).willReturn(Optional.of(item));

            // when & then
            assertThatThrownBy(() -> trashService.changeTrashItem(1L, 5L, owner))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.NOT_EXISTS_TRASH_ITEM));
        }

        @Test
        @DisplayName("같은 타입의 품목으로 변경한다")
        void 품목_변경() {
            // given
            TrashType type = petType();
            Trash trash = trashOf(owner, type, null);
            TrashItem item = TrashItem.builder()
                .id(5L).name("PET(투명 페트병)").itemType(ItemType.NORMAL).trashType(type)
                .build();
            given(trashRepository.findById(1L)).willReturn(Optional.of(trash));
            given(trashItemRepository.findById(5L)).willReturn(Optional.of(item));

            // when
            TrashResultResponse response = trashService.changeTrashItem(1L, 5L, owner);

            // then
            assertThat(trash.getTrashItem()).isSameAs(item);
            assertThat(response.itemName()).isEqualTo("PET(투명 페트병)");
            assertThat(response.typeCode()).isEqualTo(Type.PET.getTypeCode());
        }

        @Test
        @DisplayName("CAUTION 품목이면 리다이렉트 타입으로 재분류된다")
        void 주의_품목_재분류() {
            // given
            TrashType type = petType();
            TrashType redirect = TrashType.builder().id(3L).type(Type.NON_RECYCLABLE).build();
            Trash trash = trashOf(owner, type, null);
            TrashItem item = TrashItem.builder()
                .id(5L).name("오염된 페트병").itemType(ItemType.CAUTION)
                .trashType(type).redirectTrashType(redirect)
                .build();
            given(trashRepository.findById(1L)).willReturn(Optional.of(trash));
            given(trashItemRepository.findById(5L)).willReturn(Optional.of(item));

            // when
            TrashResultResponse response = trashService.changeTrashItem(1L, 5L, owner);

            // then
            assertThat(trash.getTrashType()).isSameAs(redirect);
            assertThat(response.typeCode()).isEqualTo(Type.NON_RECYCLABLE.getTypeCode());
        }

        @Test
        @DisplayName("현재 동작 문서화: 리다이렉트 타입이 없는 CAUTION 품목이면 타입이 null이 된다 (가드 조건 버그 의심)")
        void 리다이렉트_없는_주의_품목() {
            // createTrash는 redirectTrashType != null을 검사하지만
            // changeTrashItem은 item.getTrashType() != null을 검사해 null 타입이 적용된다.
            // given
            TrashType type = petType();
            Trash trash = trashOf(owner, type, null);
            TrashItem item = TrashItem.builder()
                .id(5L).name("오염된 페트병").itemType(ItemType.CAUTION)
                .trashType(type).redirectTrashType(null)
                .build();
            given(trashRepository.findById(1L)).willReturn(Optional.of(trash));
            given(trashItemRepository.findById(5L)).willReturn(Optional.of(item));

            // when
            TrashResultResponse response = trashService.changeTrashItem(1L, 5L, owner);

            // then
            assertThat(trash.getTrashType()).isNull();
            assertThat(response.typeCode()).isNull();
        }
    }

    @Nested
    @DisplayName("deleteTrash")
    class DeleteTrash {

        @Test
        @DisplayName("본인 쓰레기는 S3 이미지와 DB 레코드를 함께 삭제한다")
        void 삭제_성공() {
            // given
            Trash trash = trashOf(owner, petType(), null);
            given(trashRepository.findById(1L)).willReturn(Optional.of(trash));

            // when
            trashService.deleteTrash(1L, owner);

            // then
            verify(fileStoragePort).deleteFileByUrl("https://storage.test/trash/photo.jpg");
            verify(trashRepository).delete(trash);
        }

        @Test
        @DisplayName("다른 사용자의 쓰레기는 삭제할 수 없다")
        void 다른_사용자_삭제_불가() {
            // given
            Trash trash = trashOf(owner, petType(), null);
            given(trashRepository.findById(1L)).willReturn(Optional.of(trash));

            // when & then
            assertThatThrownBy(() -> trashService.deleteTrash(1L, otherUser))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.NOT_EXISTS_TRASH_ITEM));
            verify(fileStoragePort, never()).deleteFileByUrl(anyString());
            verify(trashRepository, never()).delete(any(Trash.class));
        }

        @Test
        @DisplayName("S3 삭제가 실패하면 S3_DELETE_FAIL 예외가 발생하고 DB 레코드는 유지된다")
        void S3_삭제_실패() {
            // given
            Trash trash = trashOf(owner, petType(), null);
            given(trashRepository.findById(1L)).willReturn(Optional.of(trash));
            willThrow(new RuntimeException("connection refused"))
                .given(fileStoragePort).deleteFileByUrl(anyString());

            // when & then
            assertThatThrownBy(() -> trashService.deleteTrash(1L, owner))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.S3_DELETE_FAIL));
            verify(trashRepository, never()).delete(any(Trash.class));
        }
    }
}
