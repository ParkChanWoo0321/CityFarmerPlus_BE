package chungbuk.cityfarmerplus.marketprice.client;

import chungbuk.cityfarmerplus.marketprice.config.KamisProperties;
import chungbuk.cityfarmerplus.marketprice.dto.MarketPriceLatestResponse.Direction;
import chungbuk.cityfarmerplus.marketprice.dto.MarketPriceLatestResponse.MarketType;
import chungbuk.cityfarmerplus.marketprice.exception.MarketPriceException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KamisMarketPriceClientTest {

    @Test
    void mapsOfficialLatestPriceJsonAndHandlesMissingPriceValues() {
        AtomicReference<URI> requestedUri = new AtomicReference<>();
        String json = """
                {
                  "condition": [["20260827"]],
                  "result_code": "000",
                  "price": [{
                    "product_cls_code": "01",
                    "category_code": "200",
                    "category_name": "채소류",
                    "productno": "361",
                    "lastest_day": "2026-08-27",
                    "item_name": "양파/양파",
                    "unit": "1kg",
                    "dpr1": "2,005",
                    "dpr2": "1,983",
                    "dpr3": [],
                    "dpr4": "2,225",
                    "direction": "1",
                    "value": "1.1"
                  }]
                }
                """;
        KamisMarketPriceClient client = new KamisMarketPriceClient(
                webClient(json, requestedUri),
                properties("dummy-key", "dummy-id")
        );

        var items = client.fetchLatest();

        assertThat(items).hasSize(1);
        assertThat(items.get(0).marketType()).isEqualTo(MarketType.RETAIL);
        assertThat(items.get(0).itemName()).isEqualTo("양파/양파");
        assertThat(items.get(0).observedDate())
                .isEqualTo(LocalDate.of(2026, 8, 27));
        assertThat(items.get(0).currentPrice()).isEqualTo(2005L);
        assertThat(items.get(0).previousMonthPrice()).isNull();
        assertThat(items.get(0).direction()).isEqualTo(Direction.UP);
        assertThat(items.get(0).changeRate()).isEqualByComparingTo("1.1");
        assertThat(requestedUri.get().getScheme()).isEqualTo("https");
        assertThat(requestedUri.get().getQuery())
                .contains("action=dailySalesList")
                .contains("p_returntype=json");
    }

    @Test
    void mapsLegacyErrorCodeAuthenticationFailureWithoutExposingCredentials() {
        KamisMarketPriceClient client = new KamisMarketPriceClient(
                webClient("{\"error_code\":\"900\",\"price\":[]}",
                        new AtomicReference<>()),
                properties("secret-key", "secret-id")
        );

        assertThatThrownBy(client::fetchLatest)
                .isInstanceOf(MarketPriceException.class)
                .hasMessage("농산물 가격정보 인증에 실패했습니다.")
                .hasMessageNotContaining("secret-key")
                .hasMessageNotContaining("secret-id");
    }

    @Test
    void mapsOfficialResultCodeAuthenticationFailure() {
        KamisMarketPriceClient client = new KamisMarketPriceClient(
                webClient("{\"result_code\":\"900\",\"price\":[]}",
                        new AtomicReference<>()),
                properties("dummy-key", "dummy-id")
        );

        assertThatThrownBy(client::fetchLatest)
                .isInstanceOf(MarketPriceException.class)
                .hasMessage("농산물 가격정보 인증에 실패했습니다.");
    }

    @Test
    void resultCodeTakesPriorityWhenBothCodeFieldsArePresent() {
        KamisMarketPriceClient client = new KamisMarketPriceClient(
                webClient(
                        "{\"result_code\":\"001\",\"error_code\":\"900\"}",
                        new AtomicReference<>()
                ),
                properties("dummy-key", "dummy-id")
        );

        assertThat(client.fetchLatest()).isEmpty();
    }

    @Test
    void rejectsMissingCredentialPairBeforeCallingProvider() {
        KamisMarketPriceClient client = new KamisMarketPriceClient(
                WebClient.builder().build(),
                properties("dummy-key", "")
        );

        assertThatThrownBy(client::fetchLatest)
                .isInstanceOf(MarketPriceException.class)
                .hasMessage("농산물 가격정보 인증 설정이 완료되지 않았습니다.");
    }

    @Test
    void noDataCodeReturnsAnEmptySnapshot() {
        KamisMarketPriceClient client = new KamisMarketPriceClient(
                webClient("{\"error_code\":\"001\"}", new AtomicReference<>()),
                properties("dummy-key", "dummy-id")
        );

        assertThat(client.fetchLatest()).isEmpty();
    }

    @Test
    void legacyErrorCodeSuccessMapsPriceEnvelope() {
        KamisMarketPriceClient client = new KamisMarketPriceClient(
                webClient(
                        "{\"error_code\":\"000\",\"price\":[]}",
                        new AtomicReference<>()
                ),
                properties("dummy-key", "dummy-id")
        );

        assertThat(client.fetchLatest()).isEmpty();
    }

    @Test
    void malformedJsonUsesInvalidResponseContract() {
        KamisMarketPriceClient client = new KamisMarketPriceClient(
                webClient("{not-json", new AtomicReference<>()),
                properties("dummy-key", "dummy-id")
        );

        assertThatThrownBy(client::fetchLatest)
                .isInstanceOf(MarketPriceException.class)
                .hasMessage("농산물 가격정보 응답 형식이 올바르지 않습니다.");
    }

    @Test
    void blankBodyUsesInvalidResponseContract() {
        KamisMarketPriceClient client = new KamisMarketPriceClient(
                webClient("   ", new AtomicReference<>()),
                properties("dummy-key", "dummy-id")
        );

        assertThatThrownBy(client::fetchLatest)
                .isInstanceOf(MarketPriceException.class)
                .hasMessage("농산물 가격정보 응답 형식이 올바르지 않습니다.");
    }

    @Test
    void invalidEnvelopeUsesInvalidResponseContract() {
        KamisMarketPriceClient client = new KamisMarketPriceClient(
                webClient("{\"error_code\":\"000\",\"price\":{}}",
                        new AtomicReference<>()),
                properties("dummy-key", "dummy-id")
        );

        assertThatThrownBy(client::fetchLatest)
                .isInstanceOf(MarketPriceException.class)
                .hasMessage("농산물 가격정보 응답 형식이 올바르지 않습니다.");
    }

    @Test
    void retriesOneServerErrorAndMapsTheSecondResponse() {
        AtomicInteger requestCount = new AtomicInteger();
        KamisMarketPriceClient client = new KamisMarketPriceClient(
                webClient(request -> {
                    if (requestCount.incrementAndGet() == 1) {
                        return Mono.just(response(HttpStatus.SERVICE_UNAVAILABLE, "{}"));
                    }
                    return Mono.just(response(
                            HttpStatus.OK,
                            "{\"error_code\":\"001\"}"
                    ));
                }),
                properties("dummy-key", "dummy-id")
        );

        assertThat(client.fetchLatest()).isEmpty();
        assertThat(requestCount).hasValue(2);
    }

    @Test
    void stopsAfterOneRetryWhenServerErrorsContinue() {
        AtomicInteger requestCount = new AtomicInteger();
        KamisMarketPriceClient client = new KamisMarketPriceClient(
                webClient(request -> {
                    requestCount.incrementAndGet();
                    return Mono.just(response(HttpStatus.BAD_GATEWAY, "{}"));
                }),
                properties("dummy-key", "dummy-id")
        );

        assertThatThrownBy(client::fetchLatest)
                .isInstanceOf(MarketPriceException.class)
                .hasMessage("농산물 가격정보를 일시적으로 불러올 수 없습니다.");
        assertThat(requestCount).hasValue(2);
    }

    @Test
    void clientErrorIsRejectedWithoutRetry() {
        AtomicInteger requestCount = new AtomicInteger();
        KamisMarketPriceClient client = new KamisMarketPriceClient(
                webClient(request -> {
                    requestCount.incrementAndGet();
                    return Mono.just(response(HttpStatus.BAD_REQUEST, "{}"));
                }),
                properties("dummy-key", "dummy-id")
        );

        assertThatThrownBy(client::fetchLatest)
                .isInstanceOf(MarketPriceException.class)
                .hasMessage("농산물 가격정보 제공자가 요청을 거부했습니다.");
        assertThat(requestCount).hasValue(1);
    }

    private WebClient webClient(
            String json,
            AtomicReference<URI> requestedUri
    ) {
        return webClient(request -> {
            requestedUri.set(request.url());
            return Mono.just(response(HttpStatus.OK, json));
        });
    }

    private WebClient webClient(ExchangeFunction exchangeFunction) {
        return WebClient.builder()
                .baseUrl("https://www.kamis.or.kr/service/price/xml.do")
                .exchangeFunction(exchangeFunction)
                .build();
    }

    private ClientResponse response(HttpStatus status, String body) {
        return ClientResponse.create(status)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build();
    }

    private KamisProperties properties(String key, String id) {
        return new KamisProperties(
                "https://www.kamis.or.kr/service/price/xml.do",
                key,
                id,
                Duration.ofSeconds(2),
                Duration.ofSeconds(5),
                Duration.ofHours(1),
                Duration.ofHours(24),
                Duration.ofSeconds(30)
        );
    }
}
