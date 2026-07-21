package com.trashheroesbe.fixture;

import com.trashheroesbe.feature.partner.domain.entity.Partner;

public class PartnerFixture {

    public static Partner partner(Long id) {
        return Partner.builder()
            .id(id)
            .partnerName("어스어스" + id)
            .email("partner" + id + "@test.com")
            .password("encoded-password")
            .build();
    }
}
