package chungbuk.cityfarmerplus.marketprice.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record MarketPriceLatestResponse(
        String provider,
        String description,
        LocalDate observedDate,
        Instant fetchedAt,
        boolean stale,
        int page,
        int size,
        long totalElements,
        int totalPages,
        List<Item> items
) {

    public enum MarketType {
        RETAIL("01"),
        WHOLESALE("02");

        private final String code;

        MarketType(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    public enum Direction {
        DOWN,
        UP,
        UNCHANGED,
        UNKNOWN
    }

    public record Item(
            MarketType marketType,
            String categoryCode,
            String categoryName,
            String productNo,
            String itemName,
            String unit,
            LocalDate observedDate,
            Long currentPrice,
            Long previousDayPrice,
            Long previousMonthPrice,
            Long previousYearPrice,
            Direction direction,
            BigDecimal changeRate
    ) {
    }
}
