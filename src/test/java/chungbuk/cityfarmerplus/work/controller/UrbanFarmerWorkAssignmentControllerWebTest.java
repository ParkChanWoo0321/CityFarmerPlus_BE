package chungbuk.cityfarmerplus.work.controller;

import chungbuk.cityfarmerplus.auth.config.SecurityConfig;
import chungbuk.cityfarmerplus.auth.exception.GlobalExceptionHandler;
import chungbuk.cityfarmerplus.common.web.PageResponse;
import chungbuk.cityfarmerplus.work.dto.WorkAssignmentResponse;
import chungbuk.cityfarmerplus.work.dto.WorkAssignmentView;
import chungbuk.cityfarmerplus.work.guide.WorkGuideService;
import chungbuk.cityfarmerplus.work.service.WorkAssignmentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UrbanFarmerWorkAssignmentController.class)
@Import({
        GlobalExceptionHandler.class,
        SecurityConfig.class
})
class UrbanFarmerWorkAssignmentControllerWebTest {

    private static final String ENDPOINT = "/api/urban-farmers/me/work-assignments";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WorkAssignmentService service;

    @MockitoBean
    private WorkGuideService guideService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void omittedViewDefaultsToAllAndKeepsExistingPagingContract() throws Exception {
        when(jwtDecoder.decode("urban-jwt")).thenReturn(jwt());
        when(service.getUrbanFarmerAssignments(
                20L,
                WorkAssignmentView.ALL,
                0,
                20
        )).thenReturn(emptyPage(0, 20));

        mockMvc.perform(get(ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer urban-jwt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));

        verify(service).getUrbanFarmerAssignments(
                20L,
                WorkAssignmentView.ALL,
                0,
                20
        );
    }

    @Test
    void forwardsUpcomingViewAndPagingToService() throws Exception {
        when(jwtDecoder.decode("urban-jwt")).thenReturn(jwt());
        when(service.getUrbanFarmerAssignments(
                20L,
                WorkAssignmentView.UPCOMING,
                1,
                5
        )).thenReturn(emptyPage(1, 5));

        mockMvc.perform(get(ENDPOINT)
                        .queryParam("view", "UPCOMING")
                        .queryParam("page", "1")
                        .queryParam("size", "5")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer urban-jwt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(5));

        verify(service).getUrbanFarmerAssignments(
                20L,
                WorkAssignmentView.UPCOMING,
                1,
                5
        );
    }

    @Test
    void rejectsUnknownViewBeforeCallingService() throws Exception {
        when(jwtDecoder.decode("urban-jwt")).thenReturn(jwt());

        mockMvc.perform(get(ENDPOINT)
                        .queryParam("view", "FUTURE")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer urban-jwt"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_PARAMETER"));

        verifyNoInteractions(service);
    }

    private PageResponse<WorkAssignmentResponse> emptyPage(int page, int size) {
        return new PageResponse<>(List.of(), page, size, 0, 0, false);
    }

    private Jwt jwt() {
        Instant issuedAt = Instant.now();
        return Jwt.withTokenValue("urban-jwt")
                .header("alg", "HS256")
                .subject("20")
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(3600))
                .claim("role", "URBAN_FARMER")
                .build();
    }
}
