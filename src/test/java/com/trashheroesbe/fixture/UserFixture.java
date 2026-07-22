package com.trashheroesbe.fixture;

import com.trashheroesbe.feature.partner.domain.entity.Partner;
import com.trashheroesbe.feature.user.domain.entity.User;
import com.trashheroesbe.feature.user.domain.type.AuthProvider;
import com.trashheroesbe.feature.user.domain.type.Role;

public class UserFixture {

    public static User user(Long id) {
        return User.builder()
            .id(id)
            .nickname("특공대요원" + id)
            .provider(AuthProvider.KAKAO)
            .role(Role.USER)
            .build();
    }

    public static User partnerUser(Long id, Partner partner) {
        return User.builder()
            .id(id)
            .nickname(partner.getPartnerName())
            .provider(AuthProvider.PARTNER)
            .role(Role.PARTNER)
            .partner(partner)
            .build();
    }
}
