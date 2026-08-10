package com.smartsoc.api.soar;

import com.smartsoc.api.common.dto.PageResponse;
import com.smartsoc.api.soar.dto.SoarDtos.DeclarePlaybookRequest;
import com.smartsoc.api.soar.dto.SoarDtos.PlaybookResponse;
import com.smartsoc.application.soar.PlaybookService;
import com.smartsoc.domain.common.PageQuery;
import com.smartsoc.domain.common.PageResult;
import com.smartsoc.domain.soar.Playbook;
import com.smartsoc.domain.soar.PlaybookQuery;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Playbooks — procédures de réponse documentées (ADR-012). Lecture : tout
 * utilisateur authentifié. Écriture (déclarer/modifier/archiver) :
 * analystes et plus. Pas de suppression : un playbook s'archive.
 */
@RestController
@RequestMapping("/api/v1/playbooks")
@RequiredArgsConstructor
public class PlaybookController {

    private static final String WRITE_ROLES = "hasAnyRole('ADMIN','SOC_MANAGER','SOC_ANALYST')";

    private final PlaybookService playbookService;
    private final PlaybookApiMapper mapper;

    @PostMapping
    @PreAuthorize(WRITE_ROLES)
    public PlaybookResponse create(@Valid @RequestBody DeclarePlaybookRequest request) {
        return mapper.toResponse(playbookService.declare(toCommand(request)));
    }

    @GetMapping
    public PageResponse<PlaybookResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "false") boolean includeArchived,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {

        PageResult<Playbook> result = playbookService.search(
                new PlaybookQuery(search, includeArchived, PageQuery.of(page, size)));
        return PageResponse.of(result, mapper::toResponse);
    }

    @GetMapping("/{id}")
    public PlaybookResponse get(@PathVariable UUID id) {
        return mapper.toResponse(playbookService.get(id));
    }

    @PatchMapping("/{id}")
    @PreAuthorize(WRITE_ROLES)
    public PlaybookResponse update(@PathVariable UUID id, @Valid @RequestBody DeclarePlaybookRequest request) {
        return mapper.toResponse(playbookService.update(id, toCommand(request)));
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize(WRITE_ROLES)
    public PlaybookResponse archive(@PathVariable UUID id) {
        return mapper.toResponse(playbookService.archive(id));
    }

    private Playbook.DeclareCommand toCommand(DeclarePlaybookRequest request) {
        return Playbook.DeclareCommand.builder()
                .name(request.name())
                .description(request.description())
                .steps(request.steps().stream().map(mapper::toDomain).toList())
                .shuffleWorkflowId(request.shuffleWorkflowId())
                .shuffleWebhookPath(request.shuffleWebhookPath())
                .build();
    }
}
