# KAMIS 최근 조사 가격 API

## 핵심 안내

- 운영 Base URL: `https://cityfarmerplus-api-82951616760.us-west1.run.app`
- 로컬 Base URL: `http://localhost:8080`
- 인증 없이 호출 가능하다.
- 실시간 거래가가 아니라 KAMIS 최근 조사일 기준 가격이다.
- 화면에 `KAMIS 최근 조사 가격`과 `observedDate`를 함께 표시한다.

## 조회

```http
GET /api/market-prices/latest?marketType=RETAIL&categoryCode=200&keyword=양파&page=0&size=20
```

`marketType`은 `RETAIL` 또는 `WHOLESALE`, `categoryCode`는 `100`, `200`, `300`, `400`, `500`, `600` 중 하나다. `keyword`는 최대 50자, `size`는 1~100이다.

응답은 `provider`, `description`, `observedDate`, `fetchedAt`, `stale`, 페이지 정보와 `items`를 반환한다. 각 item에는 시장 구분, 부류, 품목 번호·이름, 단위, 조사일, 현재·전일·전월·전년 가격, 등락 방향·비율이 포함된다. 원본에 없는 가격은 `null`이다.

`stale=true`이면 KAMIS 일시 장애로 24시간 이내의 마지막 성공값을 반환한 것이다. 프론트는 데이터를 표시하되 갱신이 지연되었음을 함께 안내한다.

오류 코드는 `KAMIS_CONFIGURATION_MISSING`, `KAMIS_AUTHENTICATION_FAILED`, `KAMIS_REJECTED_REQUEST`, `KAMIS_INVALID_RESPONSE`, `MARKET_PRICE_UNAVAILABLE`이다. 인증 키와 요청자 ID는 프론트에 전달하지 않는다.
