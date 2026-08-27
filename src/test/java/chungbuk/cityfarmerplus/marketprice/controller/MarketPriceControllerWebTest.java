package chungbuk.cityfarmerplus.marketprice.controller;

import chungbuk.cityfarmerplus.auth.config.SecurityConfig;
import chungbuk.cityfarmerplus.auth.exception.GlobalExceptionHandler;
import chungbuk.cityfarmerplus.marketprice.dto.MarketPriceLatestResponse;
import chungbuk.cityfarmerplus.marketprice.dto.MarketPriceLatestResponse.Direction;
import chungbuk.cityfarmerplus.marketprice.dto.MarketPriceLatestResponse.Item;
import chungbuk.cityfarmerplus.marketprice.dto.MarketPriceLatestResponse.MarketType;
import chungbuk.cityfarmerplus.marketprice.exception.MarketPriceException;
import chungbuk.cityfarmerplus.marketprice.service.MarketPriceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MarketPriceController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class MarketPriceControllerWebTest {

    private static final String ENDPOINT = "/api/market-prices/latest";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MarketPriceService marketPriceService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void latestPricesArePublicAndExposeObservationTimestamp() throws Exception {
        when(marketPriceService.getLatest(
                MarketType.RETAIL,
                "200",
                "양파",
                0,
                20
        )).thenReturn(response());

        mockMvc.perform(get(ENDPOINT)
                        .param("marketType", "RETAIL")
                        .param("categoryCode", "200")
                        .param("keyword", "양파"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("KAMIS"))
                .andExpect(jsonPath("$.description")
                        .value("KAMIS 최근 조사 가격"))
                .andExpect(jsonPath("$.observedDate").value("2026-08-27"))
                .andExpect(jsonPath("$.stale").value(false))
                .andExpect(jsonPath("$.items[0].itemName").value("양파/양파"))
                .andExpect(jsonPath("$.items[0].currentPrice").value(2005));

        verify(marketPriceService).getLatest(
                MarketType.RETAIL,
                "200",
                "양파",
                0,
                20
        );
    }

    @Test
    void invalidCategoryCodeIsRejected() throws Exception {
        mockMvc.perform(get(ENDPOINT).param("categoryCode", "999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void pageSizeAboveContractLimitIsRejected() throws Exception {
        mockMvc.perform(get(ENDPOINT).param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(marketPriceService);
    }

    @Test
    void omittedQueryUsesRetailAndStablePaginationDefaults() throws Exception {
        when(marketPriceService.getLatest(
                MarketType.RETAIL,
                null,
                null,
                0,
                20
        )).thenReturn(response());

        mockMvc.perform(get(ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("KAMIS"));

        verify(marketPriceService).getLatest(
                MarketType.RETAIL,
                null,
                null,
                0,
                20
        );
    }

    @Test
    void unknownMarketTypeUsesInvalidParameterContract() throws Exception {
        mockMvc.perform(get(ENDPOINT).param("marketType", "MART"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_PARAMETER"));

        verifyNoInteractions(marketPriceService);
    }

    @Test
    void negativePageIsRejectedBeforeServiceCall() throws Exception {
        mockMvc.perform(get(ENDPOINT).param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(marketPriceService);
    }

    @Test
    void keywordAboveContractLimitIsRejectedBeforeServiceCall() throws Exception {
        mockMvc.perform(get(ENDPOINT).param("keyword", "가".repeat(51)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(marketPriceService);
    }

    @Test
    void providerFailureUsesPublicDomainErrorContract() throws Exception {
        when(marketPriceService.getLatest(
                MarketType.RETAIL,
                null,
                null,
                0,
                20
        )).thenThrow(MarketPriceException.authenticationFailed());

        mockMvc.perform(get(ENDPOINT))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("KAMIS_AUTHENTICATION_FAILED"))
                .andExpect(jsonPath("$.message")
                        .value("농산물 가격정보 인증에 실패했습니다."));
    }

    private MarketPriceLatestResponse response() {
        Item item = new Item(
                MarketType.RETAIL,
                "200",
                "채소류",
                "361",
                "양파/양파",
                "1kg",
                LocalDate.of(2026, 8, 27),
                2005L,
                1983L,
                1767L,
                2225L,
                Direction.UP,
                new BigDecimal("1.1")
        );
        return new MarketPriceLatestResponse(
                "KAMIS",
                "KAMIS 최근 조사 가격",
                LocalDate.of(2026, 8, 27),
                Instant.parse("2026-08-27T00:00:00Z"),
                false,
                0,
                20,
                1,
                1,
                List.of(item)
        );
    }
}
