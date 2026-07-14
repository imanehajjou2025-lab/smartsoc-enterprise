package com.smartsoc.api.incidents;

import com.smartsoc.api.alerts.AlertApiMapper;
import com.smartsoc.api.common.dto.PageResponse;
import com.smartsoc.api.incidents.dto.IncidentDtos.AssigneeRequest;
import com.smartsoc.api.incidents.dto.IncidentDtos.CreateIncidentRequest;
import com.smartsoc.api.incidents.dto.IncidentDtos.IncidentDetailResponse;
import com.smartsoc.api.incidents.dto.IncidentDtos.IncidentResponse;
import com.smartsoc.api.incidents.dto.IncidentDtos.NoteRequest;
import com.smartsoc.api.incidents.dto.IncidentDtos.UpdateStatusRequest;
import com.smartsoc.application.incidents.IncidentService;
import com.smartsoc.application.incidents.IncidentService.CreateIncidentCommand;
import com.smartsoc.domain.incidents.IncidentQuery;
import com.smartsoc.domain.incidents.IncidentStatus;
import com.smartsoc.domain.alerts.Severity;
import com.smartsoc.domain.common.PageQuery;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Gestion des incidents. Lecture : tout utilisateur authentifié.
 * Écriture (création, triage, liens, notes) : analystes et plus.
 */
@RestController
@RequestMapping("/api/v1/incidents")
@RequiredArgsConstructor
public class IncidentController {

    private static final String WRITE_ROLES = "hasAnyRole('ADMIN','SOC_MANAGER','SOC_ANALYST')";

    private final IncidentService incidentService;
    private final IncidentApiMapper mapper;
    private final AlertApiMapper alertMapper;

    @PostMapping
    @PreAuthorize(WRITE_ROLES)
    @ResponseStatus(HttpStatus.CREATED)
    public IncidentResponse create(@Valid @RequestBody CreateIncidentRequest request,
                                   @AuthenticationPrincipal Jwt jwt) {
        return mapper.toResponse(incidentService.createIncident(
                new CreateIncidentCommand(request.title(), request.description(), request.severity()),
                jwt.getSubject()));
    }

    @PostMapping("/from-alert/{alertId}")
    @PreAuthorize(WRITE_ROLES)
    @ResponseStatus(HttpStatus.CREATED)
    public IncidentResponse escalate(@PathVariable UUID alertId, @AuthenticationPrincipal Jwt jwt) {
        return mapper.toResponse(incidentService.escalateFromAlert(alertId, jwt.getSubject()));
    }

    @GetMapping
    public PageResponse<IncidentResponse> list(
            @RequestParam(required = false) IncidentStatus status,
            @RequestParam(required = false) Severity severity,
            @RequestParam(required = false) String assignee,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        IncidentQuery query = new IncidentQuery(status, severity, assignee, PageQuery.of(page, size));
        return PageResponse.of(incidentService.search(query), mapper::toResponse);
    }

    @GetMapping("/{id}")
    public IncidentDetailResponse get(@PathVariable UUID id) {
        return new IncidentDetailResponse(
                mapper.toResponse(incidentService.getIncident(id)),
                alertMapper.toResponses(incidentService.getLinkedAlerts(id)),
                mapper.toTimelineResponses(incidentService.getTimeline(id)));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize(WRITE_ROLES)
    public IncidentResponse changeStatus(@PathVariable UUID id,
                                         @Valid @RequestBody UpdateStatusRequest request,
                                         @AuthenticationPrincipal Jwt jwt) {
        return mapper.toResponse(incidentService.changeStatus(id, request.status(), jwt.getSubject()));
    }

    @PutMapping("/{id}/assignee")
    @PreAuthorize(WRITE_ROLES)
    public IncidentResponse assign(@PathVariable UUID id,
                                   @Valid @RequestBody AssigneeRequest request,
                                   @AuthenticationPrincipal Jwt jwt) {
        return mapper.toResponse(incidentService.assign(id, request.username(), jwt.getSubject()));
    }

    @DeleteMapping("/{id}/assignee")
    @PreAuthorize(WRITE_ROLES)
    public IncidentResponse unassign(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return mapper.toResponse(incidentService.unassign(id, jwt.getSubject()));
    }

    @PostMapping("/{id}/notes")
    @PreAuthorize(WRITE_ROLES)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addNote(@PathVariable UUID id, @Valid @RequestBody NoteRequest request,
                        @AuthenticationPrincipal Jwt jwt) {
        incidentService.addNote(id, request.message(), jwt.getSubject());
    }

    @PostMapping("/{id}/alerts/{alertId}")
    @PreAuthorize(WRITE_ROLES)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void linkAlert(@PathVariable UUID id, @PathVariable UUID alertId,
                          @AuthenticationPrincipal Jwt jwt) {
        incidentService.linkAlert(id, alertId, jwt.getSubject());
    }

    @DeleteMapping("/{id}/alerts/{alertId}")
    @PreAuthorize(WRITE_ROLES)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlinkAlert(@PathVariable UUID id, @PathVariable UUID alertId,
                            @AuthenticationPrincipal Jwt jwt) {
        incidentService.unlinkAlert(id, alertId, jwt.getSubject());
    }
}
