package chungbuk.cityfarmerplus.auth.service;

/**
 * 계정 탈퇴 전에 기능별 저장 데이터와 파일을 정리하는 확장 지점이다.
 * 구현은 같은 사용자에 대해 여러 번 호출되어도 안전하도록 작성해야 한다.
 */
public interface AccountDataCleaner {

    void clean(Long userId);
}
