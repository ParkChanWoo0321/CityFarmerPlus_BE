package chungbuk.cityfarmerplus.marketprice.controller;

import chungbuk.cityfarmerplus.marketprice.dto.MarketPriceLatestResponse;
import chungbuk.cityfarmerplus.marketprice.dto.MarketPriceLatestResponse.MarketType;
import chungbuk.cityfarmerplus.marketprice.service.MarketPriceService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/market-prices")
@RequiredArgsConstructor
@Validated
public class MarketPriceController {

    private final MarketPriceService marketPriceService;

    @GetMapping("/latest")
    public ResponseEntity<MarketPriceLatestResponse> latest(
            @RequestParam(defaultValue = "RETAIL") MarketType marketType,
            @RequestParam(required = false)
            @Pattern(
                    regexp = "^(100|200|300|400|500|600)$",
                    message = "categoryCode는 KAMIS 부류 코드여야 합니다."
            )
            String categoryCode,
            @RequestParam(required = false)
            @Size(max = 50, message = "keyword는 50자 이하여야 합니다.")
            String keyword,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ResponseEntity.ok(marketPriceService.getLatest(
                marketType,
                categoryCode,
                keyword,
                page,
                size
        ));
    }
}
