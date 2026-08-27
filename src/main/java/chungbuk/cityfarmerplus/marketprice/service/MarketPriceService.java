package chungbuk.cityfarmerplus.marketprice.service;

import chungbuk.cityfarmerplus.marketprice.client.KamisMarketPriceClient;
import chungbuk.cityfarmerplus.marketprice.config.KamisProperties;
import chungbuk.cityfarmerplus.marketprice.dto.MarketPriceLatestResponse;
import chungbuk.cityfarmerplus.marketprice.dto.MarketPriceLatestResponse.Item;
import chungbuk.cityfarmerplus.marketprice.dto.MarketPriceLatestResponse.MarketType;
import chungbuk.cityfarmerplus.marketprice.exception.MarketPriceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class MarketPriceService {

    private static final String PROVIDER = "KAMIS";
    private static final String DESCRIPTION = "KAMIS 최근 조사 가격";

    private final KamisMarketPriceClient client;
    private final KamisProperties properties;
    private final Clock clock;
    private final AtomicBoolean refreshInProgress = new AtomicBoolean();
    private volatile ProviderState state = new ProviderState(null, null);

    @Autowired
    public MarketPriceService(
            KamisMarketPriceClient client,
            KamisProperties properties
    ) {
        this(client, properties, Clock.systemUTC());
    }

    MarketPriceService(
            KamisMarketPriceClient client,
            KamisProperties properties,
            Clock clock
    ) {
        this.client = client;
        this.properties = properties;
        this.clock = clock;
    }

    public MarketPriceLatestResponse getLatest(
            MarketType marketType,
            String categoryCode,
            String keyword,
            int page,
            int size
    ) {
        Snapshot snapshot = loadSnapshot();
        String normalizedCategory = normalize(categoryCode);
        String normalizedKeyword = normalize(keyword).toLowerCase(Locale.ROOT);

        List<Item> filtered = snapshot.items().stream()
                .filter(item -> item.marketType() == marketType)
                .filter(item -> normalizedCategory.isBlank()
                        || normalizedCategory.equals(item.categoryCode()))
                .filter(item -> normalizedKeyword.isBlank()
                        || item.itemName().toLowerCase(Locale.ROOT)
                        .contains(normalizedKeyword))
                .sorted(Comparator
                        .comparing(Item::categoryCode)
                        .thenComparing(Item::itemName)
                        .thenComparing(Item::productNo))
                .toList();

        int from = (int) Math.min((long) page * size, filtered.size());
        int to = (int) Math.min((long) from + size, filtered.size());
        int totalPages = filtered.isEmpty()
                ? 0
                : (int) ((filtered.size() + (long) size - 1) / size);
        LocalDate observedDate = filtered.stream()
                .map(Item::observedDate)
                .filter(date -> date != null)
                .max(LocalDate::compareTo)
                .orElse(null);

        return new MarketPriceLatestResponse(
                PROVIDER,
                DESCRIPTION,
                observedDate,
                snapshot.fetchedAt(),
                snapshot.stale(),
                page,
                size,
                filtered.size(),
                totalPages,
                List.copyOf(filtered.subList(from, to))
        );
    }

    private Snapshot loadSnapshot() {
        Instant now = clock.instant();
        ProviderState currentState = state;
        CacheEntry current = currentState.cache();
        if (current != null && now.isBefore(current.freshUntil())) {
            return current.snapshot(false);
        }

        FailureState currentFailure = currentState.failure();
        if (isBackingOff(currentFailure, now)) {
            return useFailureFallback(current, currentFailure, now);
        }

        if (!refreshInProgress.compareAndSet(false, true)) {
            return useInFlightFallback();
        }
        try {
            return refreshAsLeader();
        } finally {
            refreshInProgress.set(false);
        }
    }

    private Snapshot refreshAsLeader() {
        Instant now = clock.instant();
        ProviderState currentState = state;
        CacheEntry current = currentState.cache();
        if (current != null && now.isBefore(current.freshUntil())) {
            return current.snapshot(false);
        }

        FailureState currentFailure = currentState.failure();
        if (isBackingOff(currentFailure, now)) {
            return useFailureFallback(current, currentFailure, now);
        }

        try {
            List<Item> items = client.fetchLatest();
            Instant refreshedAt = clock.instant();
            CacheEntry refreshed = new CacheEntry(
                    items,
                    refreshedAt,
                    refreshedAt.plus(properties.cacheTtl()),
                    refreshedAt.plus(properties.staleTtl())
            );
            state = new ProviderState(refreshed, null);
            return refreshed.snapshot(false);
        } catch (MarketPriceException exception) {
            Instant failedAt = clock.instant();
            FailureState failed = new FailureState(
                    failedAt.plus(properties.failureBackoff()),
                    exception
            );
            current = state.cache();
            state = new ProviderState(current, failed);
            if (isStaleAvailable(current, failedAt)) {
                return current.snapshot(true);
            }
            throw failed.replay();
        }
    }

    private Snapshot useInFlightFallback() {
        Instant now = clock.instant();
        ProviderState currentState = state;
        CacheEntry current = currentState.cache();
        if (current != null && now.isBefore(current.freshUntil())) {
            return current.snapshot(false);
        }

        FailureState currentFailure = currentState.failure();
        if (isBackingOff(currentFailure, now)) {
            return useFailureFallback(current, currentFailure, now);
        }
        if (isStaleAvailable(current, now)) {
            return current.snapshot(true);
        }
        throw MarketPriceException.unavailable();
    }

    private Snapshot useFailureFallback(
            CacheEntry current,
            FailureState currentFailure,
            Instant now
    ) {
        if (isStaleAvailable(current, now)) {
            return current.snapshot(true);
        }
        throw currentFailure.replay();
    }

    private boolean isBackingOff(FailureState currentFailure, Instant now) {
        return currentFailure != null && now.isBefore(currentFailure.retryAfter());
    }

    private boolean isStaleAvailable(CacheEntry current, Instant now) {
        return current != null && now.isBefore(current.staleUntil());
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private record CacheEntry(
            List<Item> items,
            Instant fetchedAt,
            Instant freshUntil,
            Instant staleUntil
    ) {
        private CacheEntry {
            items = List.copyOf(items);
        }

        private Snapshot snapshot(boolean stale) {
            return new Snapshot(items, fetchedAt, stale);
        }
    }

    private record Snapshot(List<Item> items, Instant fetchedAt, boolean stale) {
    }

    private record ProviderState(CacheEntry cache, FailureState failure) {
    }

    private record FailureState(
            Instant retryAfter,
            MarketPriceException exception
    ) {
        private MarketPriceException replay() {
            return MarketPriceException.copyOf(exception);
        }
    }
}
