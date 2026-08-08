package com.smartsoc.api.connectors;

import com.smartsoc.api.connectors.dto.ConnectorDtos.ConnectorResponse;
import com.smartsoc.application.connectors.ConnectorQueryService;
import com.smartsoc.domain.common.ResourceNotFoundException;
import com.smartsoc.domain.connectors.ConnectorType;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * État des connecteurs SOC (section « Connecteurs » de la console
 * Paramètres, ADR-014) — lecture seule, restreint à l'ADMIN, même
 * périmètre que {@code /api/v1/audit-logs}.
 */
@RestController
@RequestMapping("/api/v1/connectors")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class ConnectorController {

    private final ConnectorQueryService queryService;
    private final ConnectorApiMapper mapper;

    @GetMapping
    public List<ConnectorResponse> listAll() {
        return queryService.listAll().stream().map(mapper::toResponse).toList();
    }

    @GetMapping("/{type}")
    public ConnectorResponse getOne(@PathVariable ConnectorType type) {
        return queryService.findByType(type)
                .map(mapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Connector", type.name()));
    }
}
