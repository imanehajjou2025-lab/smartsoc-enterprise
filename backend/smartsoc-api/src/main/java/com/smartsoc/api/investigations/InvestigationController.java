package com.smartsoc.api.investigations;

import com.smartsoc.api.alerts.AlertApiMapper;
import com.smartsoc.api.common.dto.PageResponse;
import com.smartsoc.api.incidents.IncidentApiMapper;
import com.smartsoc.api.investigations.dto.CaseDtos.AssigneeRequest;
import com.smartsoc.api.investigations.dto.CaseDtos.CaseDetailResponse;
import com.smartsoc.api.investigations.dto.CaseDtos.CaseResponse;
import com.smartsoc.api.investigations.dto.CaseDtos.CloseCaseRequest;
import com.smartsoc.api.investigations.dto.CaseDtos.CreateCaseRequest;
import com.smartsoc.api.investigations.dto.CaseDtos.CreateTaskRequest;
import com.smartsoc.api.investigations.dto.CaseDtos.NoteRequest;
import com.smartsoc.api.investigations.dto.CaseDtos.TaskResponse;
import com.smartsoc.api.investigations.dto.CaseDtos.UpdateStatusRequest;
import com.smartsoc.api.investigations.dto.CaseDtos.UpdateTaskRequest;
import com.smartsoc.application.investigations.CaseService;
import com.smartsoc.application.investigations.CaseService.CreateCaseCommand;
import com.smartsoc.application.investigations.CaseService.UpdateTaskCommand;
import com.smartsoc.domain.alerts.Severity;
import com.smartsoc.domain.common.PageQuery;
import com.smartsoc.domain.investigations.CaseQuery;
import com.smartsoc.domain.investigations.CaseStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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
 * Module Investigations : gestion des cas d'enquête (entité Case).
 * Lecture : tout utilisateur authentifié. Écriture : analystes et plus.
 * Un cas clôturé est immuable ; la reprise passe par un cas de suivi.
 */
@RestController
@RequestMapping("/api/v1/investigations")
@RequiredArgsConstructor
public class InvestigationController {

    private static final String WRITE_ROLES = "hasAnyRole('ADMIN','SOC_MANAGER','SOC_ANALYST')";

    private final CaseService caseService;
    private final CaseApiMapper mapper;
    private final IncidentApiMapper incidentMapper;
    private final AlertApiMapper alertMapper;

    @PostMapping
    @PreAuthorize(WRITE_ROLES)
    @ResponseStatus(HttpStatus.CREATED)
    public CaseResponse create(@Valid @RequestBody CreateCaseRequest request,
                               @AuthenticationPrincipal Jwt jwt) {
        return mapper.toResponse(caseService.createCase(
                new CreateCaseCommand(request.title(), request.description(), request.priority()),
                jwt.getSubject()));
    }

    @PostMapping("/from-incident/{incidentId}")
    @PreAuthorize(WRITE_ROLES)
    @ResponseStatus(HttpStatus.CREATED)
    public CaseResponse openFromIncident(@PathVariable UUID incidentId,
                                         @AuthenticationPrincipal Jwt jwt) {
        return mapper.toResponse(caseService.openFromIncident(incidentId, jwt.getSubject()));
    }

    /** Reprise d'une enquête clôturée : ouvre un cas de suivi lié à l'origine. */
    @PostMapping("/{id}/follow-up")
    @PreAuthorize(WRITE_ROLES)
    @ResponseStatus(HttpStatus.CREATED)
    public CaseResponse openFollowUp(@PathVariable UUID id,
                                     @Valid @RequestBody CreateCaseRequest request,
                                     @AuthenticationPrincipal Jwt jwt) {
        return mapper.toResponse(caseService.openFollowUp(id,
                new CreateCaseCommand(request.title(), request.description(), request.priority()),
                jwt.getSubject()));
    }

    @GetMapping
    public PageResponse<CaseResponse> list(
            @RequestParam(required = false) CaseStatus status,
            @RequestParam(required = false) Severity priority,
            @RequestParam(required = false) String assignee,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        CaseQuery query = new CaseQuery(status, priority, assignee, PageQuery.of(page, size));
        return PageResponse.of(caseService.search(query), mapper::toResponse);
    }

    @GetMapping("/{id}")
    public CaseDetailResponse get(@PathVariable UUID id) {
        return new CaseDetailResponse(
                mapper.toResponse(caseService.getCase(id)),
                incidentMapper.toResponses(caseService.getLinkedIncidents(id)),
                alertMapper.toResponses(caseService.getLinkedAlerts(id)),
                mapper.toTaskResponses(caseService.getTasks(id)),
                mapper.toTimelineResponses(caseService.getTimeline(id)),
                mapper.toResponses(caseService.getFollowUps(id)));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize(WRITE_ROLES)
    public CaseResponse changeStatus(@PathVariable UUID id,
                                     @Valid @RequestBody UpdateStatusRequest request,
                                     @AuthenticationPrincipal Jwt jwt) {
        return mapper.toResponse(caseService.changeStatus(id, request.status(), jwt.getSubject()));
    }

    /** Clôture formelle : conclusion obligatoire, définitive. */
    @PostMapping("/{id}/close")
    @PreAuthorize(WRITE_ROLES)
    public CaseResponse close(@PathVariable UUID id,
                              @Valid @RequestBody CloseCaseRequest request,
                              @AuthenticationPrincipal Jwt jwt) {
        return mapper.toResponse(caseService.close(id, request.conclusion(), jwt.getSubject()));
    }

    @PutMapping("/{id}/assignee")
    @PreAuthorize(WRITE_ROLES)
    public CaseResponse assign(@PathVariable UUID id,
                               @Valid @RequestBody AssigneeRequest request,
                               @AuthenticationPrincipal Jwt jwt) {
        return mapper.toResponse(caseService.assign(id, request.username(), jwt.getSubject()));
    }

    @DeleteMapping("/{id}/assignee")
    @PreAuthorize(WRITE_ROLES)
    public CaseResponse unassign(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return mapper.toResponse(caseService.unassign(id, jwt.getSubject()));
    }

    @PostMapping("/{id}/notes")
    @PreAuthorize(WRITE_ROLES)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addNote(@PathVariable UUID id, @Valid @RequestBody NoteRequest request,
                        @AuthenticationPrincipal Jwt jwt) {
        caseService.addNote(id, request.message(), jwt.getSubject());
    }

    @PostMapping("/{id}/incidents/{incidentId}")
    @PreAuthorize(WRITE_ROLES)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void linkIncident(@PathVariable UUID id, @PathVariable UUID incidentId,
                             @AuthenticationPrincipal Jwt jwt) {
        caseService.linkIncident(id, incidentId, jwt.getSubject());
    }

    @DeleteMapping("/{id}/incidents/{incidentId}")
    @PreAuthorize(WRITE_ROLES)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlinkIncident(@PathVariable UUID id, @PathVariable UUID incidentId,
                               @AuthenticationPrincipal Jwt jwt) {
        caseService.unlinkIncident(id, incidentId, jwt.getSubject());
    }

    @PostMapping("/{id}/alerts/{alertId}")
    @PreAuthorize(WRITE_ROLES)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void linkAlert(@PathVariable UUID id, @PathVariable UUID alertId,
                          @AuthenticationPrincipal Jwt jwt) {
        caseService.linkAlert(id, alertId, jwt.getSubject());
    }

    @DeleteMapping("/{id}/alerts/{alertId}")
    @PreAuthorize(WRITE_ROLES)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlinkAlert(@PathVariable UUID id, @PathVariable UUID alertId,
                            @AuthenticationPrincipal Jwt jwt) {
        caseService.unlinkAlert(id, alertId, jwt.getSubject());
    }

    @PostMapping("/{id}/tasks")
    @PreAuthorize(WRITE_ROLES)
    @ResponseStatus(HttpStatus.CREATED)
    public TaskResponse addTask(@PathVariable UUID id,
                                @Valid @RequestBody CreateTaskRequest request,
                                @AuthenticationPrincipal Jwt jwt) {
        return mapper.toTaskResponse(caseService.addTask(id, request.title(), jwt.getSubject()));
    }

    @PatchMapping("/{id}/tasks/{taskId}")
    @PreAuthorize(WRITE_ROLES)
    public TaskResponse updateTask(@PathVariable UUID id, @PathVariable UUID taskId,
                                   @Valid @RequestBody UpdateTaskRequest request,
                                   @AuthenticationPrincipal Jwt jwt) {
        return mapper.toTaskResponse(caseService.updateTask(id, taskId,
                new UpdateTaskCommand(request.title(), request.status(), request.assignee()),
                jwt.getSubject()));
    }
}
