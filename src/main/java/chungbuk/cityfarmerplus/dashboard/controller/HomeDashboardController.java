package chungbuk.cityfarmerplus.dashboard.controller;

import chungbuk.cityfarmerplus.common.web.AuthenticatedUser;
import chungbuk.cityfarmerplus.dashboard.dto.FarmHomeResponse;
import chungbuk.cityfarmerplus.dashboard.dto.UrbanFarmerHomeResponse;
import chungbuk.cityfarmerplus.dashboard.service.FarmHomeService;
import chungbuk.cityfarmerplus.dashboard.service.UrbanFarmerHomeService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class HomeDashboardController {

    private final UrbanFarmerHomeService urbanFarmerHomeService;
    private final FarmHomeService farmHomeService;

    @GetMapping("/urban-farmers/me/home")
    @PreAuthorize("hasRole('URBAN_FARMER')")
    public UrbanFarmerHomeResponse urbanFarmerHome(Authentication authentication) {
        return urbanFarmerHomeService.get(AuthenticatedUser.id(authentication));
    }

    @GetMapping("/farm/me/home")
    @PreAuthorize("hasRole('FARM')")
    public FarmHomeResponse farmHome(Authentication authentication) {
        return farmHomeService.get(AuthenticatedUser.id(authentication));
    }
}
