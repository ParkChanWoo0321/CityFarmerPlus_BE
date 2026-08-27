package chungbuk.cityfarmerplus.marketprice.service;

import chungbuk.cityfarmerplus.marketprice.client.KamisMarketPriceClient;
import chungbuk.cityfarmerplus.marketprice.config.KamisProperties;
import chungbuk.cityfarmerplus.marketprice.dto.MarketPriceLatestResponse;
import chungbuk.cityfarmerplus.marketprice.dto.MarketPriceLatestResponse.Direction;
import chungbuk.cityfarmerplus.marketprice.dto.MarketPriceLatestResponse.Item;
import chungbuk.cityfarmerplus.marketprice.dto.MarketPriceLatestResponse.MarketType;
import chungbuk.cityfarmerplus.marketprice.exception.MarketPriceException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketPriceServiceTest {

    @Test
    void filtersPaginatesAndCachesLatestPrices() {
        KamisMarketPriceClient client = mock(KamisMarketPriceClient.class);
        when(client.fetchLatest()).thenReturn(List.of(
                item(MarketType.RETAIL, "200", "양파/양파"),
                item(MarketType.RETAIL, "200", "배추/여름"),
                item(MarketType.WHOLESALE, "200", "양파/양파")
        ));
        MarketPriceService service = new MarketPriceService(
                client,
                properties(),
                Clock.fixed(Instant.parse("2026-08-27T00:00:00Z"), ZoneOffset.UTC)
        );

        var first = service.getLatest(
                MarketType.RETAIL,
                "200",
                "양파",
                0,
                20
        );
        var second = service.getLatest(
                MarketType.RETAIL,
                null,
                null,
                0,
                1
        );

        assertThat(first.provider()).isEqualTo("KAMIS");
        assertThat(first.description()).isEqualTo("KAMIS 최근 조사 가격");
        assertThat(first.items()).extracting(Item::itemName)
                .containsExactly("양파/양파");
        assertThat(first.stale()).isFalse();
        assertThat(second.totalElements()).isEqualTo(2);
        assertThat(second.totalPages()).isEqualTo(2);
        assertThat(second.items()).hasSize(1);
        verify(client, times(1)).fetchLatest();
    }

    @Test
    void backsOffWithStaleCacheAndRecoversAtTheRetryBoundary() {
        KamisMarketPriceClient client = mock(KamisMarketPriceClient.class);
        when(client.fetchLatest())
                .thenReturn(List.of(item(MarketType.RETAIL, "200", "양파/양파")))
                .thenThrow(MarketPriceException.unavailable())
                .thenReturn(List.of(item(MarketType.RETAIL, "200", "배추")));
        MutableClock clock = new MutableClock(
                Instant.parse("2026-08-27T00:00:00Z")
        );
        MarketPriceService service = new MarketPriceService(
                client,
                properties(),
                clock
        );

        service.getLatest(MarketType.RETAIL, null, null, 0, 20);
        clock.advance(Duration.ofHours(2));
        var stale = service.getLatest(MarketType.RETAIL, null, null, 0, 20);
        var repeated = service.getLatest(
                MarketType.RETAIL,
                null,
                null,
                0,
                20
        );
        clock.advance(Duration.ofSeconds(29));
        var beforeBoundary = service.getLatest(
                MarketType.RETAIL,
                null,
                null,
                0,
                20
        );

        assertThat(stale.stale()).isTrue();
        assertThat(stale.items()).hasSize(1);
        assertThat(repeated.stale()).isTrue();
        assertThat(beforeBoundary.stale()).isTrue();
        verify(client, times(2)).fetchLatest();

        clock.advance(Duration.ofSeconds(1));
        var recovered = service.getLatest(
                MarketType.RETAIL,
                null,
                null,
                0,
                20
        );

        assertThat(recovered.stale()).isFalse();
        assertThat(recovered.items()).extracting(Item::itemName)
                .containsExactly("배추");
        verify(client, times(3)).fetchLatest();
    }

    @Test
    void normalizesFiltersAndSortsBeforePagination() {
        KamisMarketPriceClient client = mock(KamisMarketPriceClient.class);
        when(client.fetchLatest()).thenReturn(List.of(
                item(MarketType.RETAIL, "200", "apple/Gala"),
                item(MarketType.WHOLESALE, "200", "Apple/Wholesale"),
                item(MarketType.RETAIL, "100", "Apple/OtherCategory"),
                item(MarketType.RETAIL, "200", "Banana"),
                item(MarketType.RETAIL, "200", "Apple/Fuji")
        ));
        MarketPriceService service = new MarketPriceService(
                client,
                properties(),
                Clock.fixed(Instant.parse("2026-08-27T00:00:00Z"), ZoneOffset.UTC)
        );

        var response = service.getLatest(
                MarketType.RETAIL,
                " 200 ",
                " APPLE ",
                0,
                10
        );

        assertThat(response.totalElements()).isEqualTo(2);
        assertThat(response.totalPages()).isEqualTo(1);
        assertThat(response.items()).extracting(Item::itemName)
                .containsExactly("Apple/Fuji", "apple/Gala");
        assertThat(response.observedDate()).isEqualTo(LocalDate.of(2026, 8, 27));
    }

    @Test
    void pageBeyondLastPageReturnsEmptyItemsAndKeepsTotals() {
        KamisMarketPriceClient client = mock(KamisMarketPriceClient.class);
        when(client.fetchLatest()).thenReturn(List.of(
                item(MarketType.RETAIL, "200", "감자"),
                item(MarketType.RETAIL, "200", "배추"),
                item(MarketType.RETAIL, "200", "양파")
        ));
        MarketPriceService service = new MarketPriceService(
                client,
                properties(),
                Clock.fixed(Instant.parse("2026-08-27T00:00:00Z"), ZoneOffset.UTC)
        );

        var response = service.getLatest(
                MarketType.RETAIL,
                null,
                null,
                2,
                2
        );

        assertThat(response.page()).isEqualTo(2);
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.totalElements()).isEqualTo(3);
        assertThat(response.totalPages()).isEqualTo(2);
        assertThat(response.items()).isEmpty();
    }

    @Test
    void veryLargePageReturnsEmptyItemsWithoutIntegerOverflow() {
        KamisMarketPriceClient client = mock(KamisMarketPriceClient.class);
        when(client.fetchLatest()).thenReturn(List.of(
                item(MarketType.RETAIL, "200", "양파")
        ));
        MarketPriceService service = new MarketPriceService(
                client,
                properties(),
                Clock.fixed(Instant.parse("2026-08-27T00:00:00Z"), ZoneOffset.UTC)
        );

        var response = service.getLatest(
                MarketType.RETAIL,
                null,
                null,
                Integer.MAX_VALUE,
                100
        );

        assertThat(response.page()).isEqualTo(Integer.MAX_VALUE);
        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.items()).isEmpty();
    }

    @Test
    void noDataSnapshotHasZeroPagesAndNoObservedDate() {
        KamisMarketPriceClient client = mock(KamisMarketPriceClient.class);
        when(client.fetchLatest()).thenReturn(List.of());
        Instant fetchedAt = Instant.parse("2026-08-27T00:00:00Z");
        MarketPriceService service = new MarketPriceService(
                client,
                properties(),
                Clock.fixed(fetchedAt, ZoneOffset.UTC)
        );

        var response = service.getLatest(
                MarketType.RETAIL,
                null,
                null,
                0,
                20
        );

        assertThat(response.observedDate()).isNull();
        assertThat(response.fetchedAt()).isEqualTo(fetchedAt);
        assertThat(response.stale()).isFalse();
        assertThat(response.totalElements()).isZero();
        assertThat(response.totalPages()).isZero();
        assertThat(response.items()).isEmpty();
    }

    @Test
    void providerFailureIsRethrownAfterStaleWindowExpires() {
        KamisMarketPriceClient client = mock(KamisMarketPriceClient.class);
        MarketPriceException unavailable = MarketPriceException.unavailable();
        when(client.fetchLatest())
                .thenReturn(List.of(item(MarketType.RETAIL, "200", "양파")))
                .thenThrow(unavailable);
        MutableClock clock = new MutableClock(
                Instant.parse("2026-08-27T00:00:00Z")
        );
        MarketPriceService service = new MarketPriceService(
                client,
                properties(),
                clock
        );

        service.getLatest(MarketType.RETAIL, null, null, 0, 20);
        clock.advance(Duration.ofHours(25));

        assertThatThrownBy(() -> service.getLatest(
                MarketType.RETAIL,
                null,
                null,
                0,
                20
        )).isInstanceOf(MarketPriceException.class)
                .isNotSameAs(unavailable)
                .hasMessage(unavailable.getMessage());
        verify(client, times(2)).fetchLatest();
    }

    @Test
    void replaysOriginalFailureDuringBackoffAndRetriesAtExactBoundary() {
        KamisMarketPriceClient client = mock(KamisMarketPriceClient.class);
        MarketPriceException original = MarketPriceException.authenticationFailed();
        when(client.fetchLatest())
                .thenThrow(original)
                .thenReturn(List.of(item(MarketType.RETAIL, "200", "양파")));
        MutableClock clock = new MutableClock(
                Instant.parse("2026-08-27T00:00:00Z")
        );
        MarketPriceService service = new MarketPriceService(
                client,
                properties(),
                clock
        );

        MarketPriceException first = assertThrows(
                MarketPriceException.class,
                () -> latest(service)
        );
        MarketPriceException repeated = assertThrows(
                MarketPriceException.class,
                () -> latest(service)
        );

        assertThat(first).isNotSameAs(original);
        assertThat(repeated).isNotSameAs(first);
        assertThat(repeated.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(repeated.getCode()).isEqualTo("KAMIS_AUTHENTICATION_FAILED");
        assertThat(repeated.getMessage()).isEqualTo(original.getMessage());
        verify(client, times(1)).fetchLatest();

        clock.advance(Duration.ofSeconds(29));
        MarketPriceException beforeBoundary = assertThrows(
                MarketPriceException.class,
                () -> latest(service)
        );
        assertThat(beforeBoundary).isNotSameAs(repeated);
        assertThat(beforeBoundary.getCode()).isEqualTo(original.getCode());
        verify(client, times(1)).fetchLatest();

        clock.advance(Duration.ofSeconds(1));
        var recovered = latest(service);

        assertThat(recovered.stale()).isFalse();
        assertThat(recovered.items()).extracting(Item::itemName)
                .containsExactly("양파");
        verify(client, times(2)).fetchLatest();
    }

    @Test
    void concurrentRequestWithoutCacheFailsFastWhileLeaderRefreshes()
            throws Exception {
        KamisMarketPriceClient client = mock(KamisMarketPriceClient.class);
        CountDownLatch refreshStarted = new CountDownLatch(1);
        CountDownLatch releaseRefresh = new CountDownLatch(1);
        when(client.fetchLatest()).thenAnswer(invocation -> {
            refreshStarted.countDown();
            if (!releaseRefresh.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("refresh was not released");
            }
            return List.of(item(MarketType.RETAIL, "200", "양파"));
        });
        MarketPriceService service = new MarketPriceService(
                client,
                properties(),
                Clock.fixed(
                        Instant.parse("2026-08-27T00:00:00Z"),
                        ZoneOffset.UTC
                )
        );
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> leader = executor.submit(() -> latest(service));
            assertThat(refreshStarted.await(2, TimeUnit.SECONDS)).isTrue();

            Future<MarketPriceException> follower = executor.submit(() ->
                    assertThrows(MarketPriceException.class, () -> latest(service))
            );
            MarketPriceException failure = follower.get(1, TimeUnit.SECONDS);

            assertThat(failure.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(failure.getCode()).isEqualTo("MARKET_PRICE_UNAVAILABLE");
            assertThat(leader.isDone()).isFalse();
            verify(client, times(1)).fetchLatest();

            releaseRefresh.countDown();
            leader.get(2, TimeUnit.SECONDS);
            assertThat(latest(service).stale()).isFalse();
            verify(client, times(1)).fetchLatest();
        } finally {
            releaseRefresh.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentRequestUsesStaleWithoutWaitingForLeaderRefresh()
            throws Exception {
        KamisMarketPriceClient client = mock(KamisMarketPriceClient.class);
        CountDownLatch refreshStarted = new CountDownLatch(1);
        CountDownLatch releaseRefresh = new CountDownLatch(1);
        when(client.fetchLatest())
                .thenReturn(List.of(item(MarketType.RETAIL, "200", "양파")))
                .thenAnswer(invocation -> {
                    refreshStarted.countDown();
                    if (!releaseRefresh.await(5, TimeUnit.SECONDS)) {
                        throw new AssertionError("refresh was not released");
                    }
                    return List.of(item(MarketType.RETAIL, "200", "배추"));
                });
        MutableClock clock = new MutableClock(
                Instant.parse("2026-08-27T00:00:00Z")
        );
        MarketPriceService service = new MarketPriceService(
                client,
                properties(),
                clock
        );
        latest(service);
        clock.advance(Duration.ofHours(2));
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> leader = executor.submit(() -> latest(service));
            assertThat(refreshStarted.await(2, TimeUnit.SECONDS)).isTrue();

            Future<MarketPriceLatestResponse> follower = executor.submit(
                    () -> latest(service)
            );
            var stale = follower.get(1, TimeUnit.SECONDS);

            assertThat(stale.stale()).isTrue();
            assertThat(stale.items()).extracting(Item::itemName)
                    .containsExactly("양파");
            assertThat(leader.isDone()).isFalse();
            verify(client, times(2)).fetchLatest();

            releaseRefresh.countDown();
            leader.get(2, TimeUnit.SECONDS);
            var fresh = latest(service);
            assertThat(fresh.stale()).isFalse();
            assertThat(fresh.items()).extracting(Item::itemName)
                    .containsExactly("배추");
            verify(client, times(2)).fetchLatest();
        } finally {
            releaseRefresh.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void defaultsFailureBackoffToThirtySeconds() {
        KamisProperties properties = new KamisProperties(
                "https://www.kamis.or.kr/service/price/xml.do",
                "dummy-key",
                "dummy-id",
                Duration.ofSeconds(2),
                Duration.ofSeconds(5),
                Duration.ofHours(1),
                Duration.ofHours(24),
                null
        );

        assertThat(properties.failureBackoff()).isEqualTo(Duration.ofSeconds(30));
    }

    private MarketPriceLatestResponse latest(
            MarketPriceService service
    ) {
        return service.getLatest(MarketType.RETAIL, null, null, 0, 20);
    }

    private Item item(
            MarketType marketType,
            String categoryCode,
            String itemName
    ) {
        return new Item(
                marketType,
                categoryCode,
                "채소류",
                "361",
                itemName,
                "1kg",
                LocalDate.of(2026, 8, 27),
                2005L,
                1983L,
                1767L,
                2225L,
                Direction.UP,
                new BigDecimal("1.1")
        );
    }

    private KamisProperties properties() {
        return new KamisProperties(
                "https://www.kamis.or.kr/service/price/xml.do",
                "dummy-key",
                "dummy-id",
                Duration.ofSeconds(2),
                Duration.ofSeconds(5),
                Duration.ofHours(1),
                Duration.ofHours(24),
                Duration.ofSeconds(30)
        );
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
