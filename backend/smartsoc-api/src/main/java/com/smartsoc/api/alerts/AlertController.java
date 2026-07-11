package com.smartsoc.api.alerts;

import com.smartsoc.api.alerts.dto.AlertDtos.AlertResponse;
import com.smartsoc.api.alerts.dto.AlertDtos.UpdateAlertStatusRequest;
import com.smartsoc.api.common.dto.PageResponse;
import com.smartsoc.application.alerts.AlertTriageService;
import com.smartsoc.domain.alerts.AlertQuery;
import com.smartsoc.domain.alerts.AlertStatus;
import com.smartsoc.domain.alerts.Severity;
import com.smartsoc.domain.common.PageQuery;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Consultation et triage des alertes. Lecture : tout utilisateur
 * authentifié (VIEWER inclus). Triage : analystes et plus.
 */
@RestController
@RequestMapping("/api/v1/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertTriageService triageService;
    private final AlertApiMapper mapper;

    @GetMapping
    public PageResponse<AlertResponse> list(
            @RequestParam(required = false) AlertStatus status,
            @RequestParam(required = false) Severity severity,
            @RequestParam(required = false) String source,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        AlertQuery query = new AlertQuery(status, severity, source, PageQuery.of(page, size));
        return PageResponse.of(triageService.search(query), mapper::toResponse);
    }

    @GetMapping("/{id}")
    public AlertResponse get(@PathVariable UUID id) {
        return mapper.toResponse(triageService.getAlert(id));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'SOC_MANAGER', 'SOC_ANALYST')")
    public AlertResponse changeStatus(@PathVariable UUID id,
                                      @Valid @RequestBody UpdateAlertStatusRequest request) {
        return mapper.toResponse(triageService.changeStatus(id, request.status()));
    }
}
