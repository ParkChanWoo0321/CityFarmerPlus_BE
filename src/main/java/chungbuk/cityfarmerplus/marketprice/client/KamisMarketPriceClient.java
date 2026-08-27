package chungbuk.cityfarmerplus.marketprice.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import chungbuk.cityfarmerplus.marketprice.config.KamisProperties;
import chungbuk.cityfarmerplus.marketprice.dto.MarketPriceLatestResponse.Direction;
import chungbuk.cityfarmerplus.marketprice.dto.MarketPriceLatestResponse.Item;
import chungbuk.cityfarmerplus.marketprice.dto.MarketPriceLatestResponse.MarketType;
import chungbuk.cityfarmerplus.marketprice.exception.MarketPriceException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

@Component
public class KamisMarketPriceClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final WebClient webClient;
    private final KamisProperties properties;

    public KamisMarketPriceClient(
            @Qualifier("kamisWebClient") WebClient webClient,
            KamisProperties properties
    ) {
        this.webClient = webClient;
        this.properties = properties;
    }

    public List<Item> fetchLatest() {
        if (!properties.configured()) {
            throw MarketPriceException.configurationMissing();
        }

        String responseBody;
        try {
            responseBody = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .queryParam("action", "dailySalesList")
                            .queryParam("p_cert_key", properties.certKey())
                            .queryParam("p_cert_id", properties.certId())
                            .queryParam("p_returntype", "json")
                            .build())
                    .retrieve()
                    .onStatus(HttpStatusCode::is5xxServerError, ignored ->
                            Mono.error(new TransientUpstreamException()))
                    .onStatus(HttpStatusCode::is4xxClientError, ignored ->
                            Mono.error(MarketPriceException.rejectedRequest()))
                    .bodyToMono(String.class)
                    .timeout(properties.responseTimeout())
                    .retryWhen(Retry.max(1)
                            .filter(this::isRetryable)
                            .onRetryExhaustedThrow((spec, signal) -> signal.failure()))
                    .block();
        } catch (MarketPriceException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw MarketPriceException.unavailable();
        }

        if (responseBody == null || responseBody.isBlank()) {
            throw MarketPriceException.invalidResponse();
        }
        try {
            return mapResponse(OBJECT_MAPPER.readTree(responseBody));
        } catch (MarketPriceException exception) {
            throw exception;
        } catch (Exception exception) {
            throw MarketPriceException.invalidResponse();
        }
    }

    private boolean isRetryable(Throwable throwable) {
        return throwable instanceof TransientUpstreamException
                || throwable instanceof WebClientRequestException
                || throwable instanceof TimeoutException;
    }

    private List<Item> mapResponse(JsonNode response) {
        String code = firstText(response, "result_code", "error_code");
        if ("001".equals(code)) {
            return List.of();
        }
        if ("900".equals(code)) {
            throw MarketPriceException.authenticationFailed();
        }
        if ("200".equals(code)) {
            throw MarketPriceException.rejectedRequest();
        }
        JsonNode prices = response.get("price");
        if (!"000".equals(code) || prices == null || !prices.isArray()) {
            throw MarketPriceException.invalidResponse();
        }

        List<Item> items = new ArrayList<>();
        for (JsonNode node : prices) {
            Item item = mapPrice(node);
            if (item != null) {
                items.add(item);
            }
        }
        return List.copyOf(items);
    }

    private Item mapPrice(JsonNode price) {
        MarketType marketType = switch (text(price, "product_cls_code")) {
            case "01" -> MarketType.RETAIL;
            case "02" -> MarketType.WHOLESALE;
            default -> null;
        };
        String itemName = text(price, "item_name");
        if (marketType == null || itemName.isBlank()) {
            return null;
        }

        return new Item(
                marketType,
                text(price, "category_code"),
                text(price, "category_name"),
                text(price, "productno"),
                itemName,
                text(price, "unit"),
                parseDate(firstText(price, "lastest_day", "lastest_date")),
                parsePrice(price.get("dpr1")),
                parsePrice(price.get("dpr2")),
                parsePrice(price.get("dpr3")),
                parsePrice(price.get("dpr4")),
                parseDirection(text(price, "direction")),
                parseDecimal(price.get("value"))
        );
    }

    private String text(JsonNode node, String field) {
        if (node == null) {
            return "";
        }
        JsonNode value = node.get(field);
        return value == null || !value.isValueNode()
                ? ""
                : normalize(value.asText());
    }

    private String firstText(JsonNode node, String first, String second) {
        String value = text(node, first);
        return value.isBlank() ? text(node, second) : value;
    }

    private LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(normalize(value));
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private Long parsePrice(JsonNode value) {
        if (value == null || value.isNull() || value.isArray()
                || value.isObject()) {
            return null;
        }
        String normalized = normalize(value.asText()).replace(",", "");
        if (normalized.isBlank() || "-".equals(normalized)) {
            return null;
        }
        try {
            return Long.valueOf(normalized);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private BigDecimal parseDecimal(JsonNode value) {
        if (value == null || value.isNull() || value.isArray()
                || value.isObject()) {
            return null;
        }
        try {
            return new BigDecimal(normalize(value.asText()).replace(",", ""));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Direction parseDirection(String value) {
        return switch (normalize(value)) {
            case "0" -> Direction.DOWN;
            case "1" -> Direction.UP;
            case "2" -> Direction.UNCHANGED;
            default -> Direction.UNKNOWN;
        };
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class TransientUpstreamException extends RuntimeException {
    }

}
