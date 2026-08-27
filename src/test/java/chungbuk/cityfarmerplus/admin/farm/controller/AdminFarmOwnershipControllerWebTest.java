package chungbuk.cityfarmerplus.admin.farm.controller;

import chungbuk.cityfarmerplus.admin.farm.service.AdminFarmOwnershipService;
import chungbuk.cityfarmerplus.auth.config.SecurityConfig;
import chungbuk.cityfarmerplus.auth.exception.GlobalExceptionHandler;
import chungbuk.cityfarmerplus.farm.entity.FarmProfile;
import chungbuk.cityfarmerplus.farm.ownership.dto.FarmOwnershipDocumentDownload;
import chungbuk.cityfarmerplus.farm.ownership.service.FarmOwnershipQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminFarmOwnershipController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class AdminFarmOwnershipControllerWebTest {

    private static final String ENDPOINT =
            "/api/admin/farm-profiles/20/ownership/documents/30";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminFarmOwnershipService farmOwnershipService;

    @MockitoBean
    private FarmOwnershipQueryService farmOwnershipQueryService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void centerAdminListsEveryFarmProfileWhenStatusIsOmitted() throws Exception {
        when(jwtDecoder.decode("admin-jwt")).thenReturn(jwt("30", "CENTER_ADMIN"));
        when(farmOwnershipService.list(null)).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/farm-profiles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-jwt"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verify(farmOwnershipService).list(null);
    }

    @Test
    void centerAdminFiltersFarmProfilesByStatus() throws Exception {
        when(jwtDecoder.decode("admin-jwt")).thenReturn(jwt("30", "CENTER_ADMIN"));
        when(farmOwnershipService.list(FarmProfile.FarmProfileStatus.PENDING_REVIEW))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/admin/farm-profiles")
                        .param("status", "PENDING_REVIEW")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-jwt"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verify(farmOwnershipService).list(
                FarmProfile.FarmProfileStatus.PENDING_REVIEW
        );
    }

    @Test
    void centerAdminDownloadsFarmOwnershipEvidence() throws Exception {
        when(jwtDecoder.decode("admin-jwt")).thenReturn(jwt("30", "CENTER_ADMIN"));
        when(farmOwnershipQueryService.downloadForAdmin(20L, 30L)).thenReturn(
                new FarmOwnershipDocumentDownload(
                        new ByteArrayResource("proof".getBytes()),
                        "토지대장.pdf",
                        "application/pdf",
                        5L
                )
        );

        mockMvc.perform(get(ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-jwt"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string(
                        HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.containsString("attachment")
                ))
                .andExpect(content().bytes("proof".getBytes()));
    }

    @Test
    void nonCenterAdminCannotDownloadFarmOwnershipEvidence() throws Exception {
        when(jwtDecoder.decode("urban-jwt")).thenReturn(jwt("15", "URBAN_FARMER"));

        mockMvc.perform(get(ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer urban-jwt"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(farmOwnershipQueryService);
    }

    private Jwt jwt(String subject, String role) {
        Instant issuedAt = Instant.now();
        return Jwt.withTokenValue(role + "-jwt")
                .header("alg", "HS256")
                .subject(subject)
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(3600))
                .claim("role", role)
                .build();
    }
}
