package com.smartsoc.api.audit;

import com.smartsoc.api.audit.dto.AuditLogDtos.AuditLogEntryResponse;
import com.smartsoc.api.common.dto.PageResponse;
import com.smartsoc.application.audit.AuditLogQueryService;
import com.smartsoc.domain.audit.AuditAction;
import com.smartsoc.domain.audit.AuditLogQuery;
import com.smartsoc.domain.common.PageQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Journal d'audit (console Paramètres) — restreint à l'ADMIN, même
 * périmètre que {@code /api/v1/users}.
 */
@RestController
@RequestMapping("/api/v1/audit-logs")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogQueryService queryService;
    private final AuditLogApiMapper mapper;

    @GetMapping
    public PageResponse<AuditLogEntryResponse> search(
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) String actorUsername,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return PageResponse.of(queryService.search(new AuditLogQuery(
                action, actorUsername, from, to, null, null, PageQuery.of(page, size))), mapper::toResponse);
    }
}
