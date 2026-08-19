package chungbuk.cityfarmerplus.support.controller;

import chungbuk.cityfarmerplus.support.dto.FaqResponse;
import chungbuk.cityfarmerplus.support.service.FaqService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/support/faqs")
@RequiredArgsConstructor
public class FaqController {

    private final FaqService service;

    @GetMapping
    public List<FaqResponse> getAll() {
        return service.getAll();
    }
}
