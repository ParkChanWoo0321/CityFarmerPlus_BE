package chungbuk.cityfarmerplus.common.region;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ChungbukCityCounty {
    CHEONGJU("청주시"),
    CHUNGJU("충주시"),
    JECHEON("제천시"),
    BOEUN("보은군"),
    OKCHEON("옥천군"),
    YEONGDONG("영동군"),
    JEUNGPYEONG("증평군"),
    JINCHEON("진천군"),
    GOESAN("괴산군"),
    EUMSEONG("음성군"),
    DANYANG("단양군");

    private final String koreanName;
}
