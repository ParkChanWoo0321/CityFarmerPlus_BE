# KAMIS 최근 조사 가격 API

- 기준일: 2026-08-27
- 운영 Base URL: `https://cityfarmerplus-api-82951616760.us-west1.run.app`
- 로컬 Base URL: `http://localhost:8080`
- 인증: 없음
- 제공자: 한국농수산식품유통공사 KAMIS

KAMIS 가격은 실시간 거래 체결가나 농가 실수취가가 아니다. 최근 조사일 기준의 도·소매 조사 가격이며 프론트 화면에도 반드시 `KAMIS 최근 조사 가격`과 `observedDate`를 표시한다.

## 최근 가격 조회

```http
GET /api/market-prices/latest?marketType=RETAIL&categoryCode=200&keyword=양파&page=0&size=20
```

| Query | 필수 | 값 |
|---|---|---|
| `marketType` | 아니오 | `RETAIL` 기본, 또는 `WHOLESALE` |
| `categoryCode` | 아니오 | `100` 식량, `200` 채소, `300` 특용, `400` 과일, `500` 축산, `600` 수산 |
| `keyword` | 아니오 | 품목명 부분검색, 최대 50자 |
| `page` | 아니오 | 0 이상, 기본 0 |
| `size` | 아니오 | 1~100, 기본 20 |

성공 `200 OK`:

```json
{
  "provider": "KAMIS",
  "description": "KAMIS 최근 조사 가격",
  "observedDate": "2026-08-27",
  "fetchedAt": "2026-08-27T03:00:00Z",
  "stale": false,
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1,
  "items": [
    {
      "marketType": "RETAIL",
      "categoryCode": "200",
      "categoryName": "채소류",
      "productNo": "361",
      "itemName": "양파/양파",
      "unit": "1kg",
      "observedDate": "2026-08-27",
      "currentPrice": 2005,
      "previousDayPrice": 1983,
      "previousMonthPrice": 1767,
      "previousYearPrice": 2225,
      "direction": "UP",
      "changeRate": 1.1
    }
  ]
}
```

가격은 원 단위 정수이며 KAMIS 원본에 값이 없으면 `null`이다. `direction`은 `DOWN`, `UP`, `UNCHANGED`, `UNKNOWN` 중 하나다. 검색 결과가 없으면 오류가 아니라 `items: []`, `totalElements: 0`을 반환한다.

## 캐시와 장애 처리

- 최근 성공 응답은 1시간 동안 재사용한다.
- 새 호출이 실패해도 24시간 이내의 마지막 성공값이 있으면 `stale=true`로 반환한다.
- KAMIS 호출 실패 후 기본 30초 동안 재호출하지 않는다. 이 구간에는 마지막 성공값을 즉시 반환하고, 값이 없으면 직전 오류 계약을 즉시 재현한다. 간격은 `KAMIS_FAILURE_BACKOFF`로 조정한다.
- 갱신 호출이 이미 진행 중이면 다른 요청은 기다리지 않는다. 마지막 성공값이 있으면 `stale=true`, 없으면 `503 MARKET_PRICE_UNAVAILABLE`을 즉시 반환한다.
- 마지막 성공값도 없으면 `503 MARKET_PRICE_UNAVAILABLE`을 반환한다.
- KAMIS 인증 실패는 `503 KAMIS_AUTHENTICATION_FAILED`이다.
- KAMIS 요청 거부는 `502 KAMIS_REJECTED_REQUEST`, 해석할 수 없는 응답은 `502 KAMIS_INVALID_RESPONSE`이다.
- 서버에 `KAMIS_API_KEY`와 `KAMIS_CERT_ID` 중 하나라도 없으면 `503 KAMIS_CONFIGURATION_MISSING`이다.

KAMIS 인증값은 응답·로그·프론트 환경변수에 절대 노출하지 않는다.
