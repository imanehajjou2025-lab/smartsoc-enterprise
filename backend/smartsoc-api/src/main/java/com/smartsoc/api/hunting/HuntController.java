package com.smartsoc.api.hunting;

import com.smartsoc.api.common.dto.PageResponse;
import com.smartsoc.api.hunting.dto.HuntDtos.DeclareHuntRequest;
import com.smartsoc.api.hunting.dto.HuntDtos.ExecuteAdHocRequest;
import com.smartsoc.api.hunting.dto.HuntDtos.HuntExecutionResponse;
import com.smartsoc.api.hunting.dto.HuntDtos.HuntFieldDescriptor;
import com.smartsoc.api.hunting.dto.HuntDtos.HuntResponse;
import com.smartsoc.application.hunting.HuntExecutionService;
import com.smartsoc.application.hunting.HuntQueryService;
import com.smartsoc.domain.common.PageQuery;
import com.smartsoc.domain.common.PageResult;
import com.smartsoc.domain.hunting.HuntQuery;
import com.smartsoc.domain.hunting.HuntQueryFilter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Requêtes de chasse — chasse proactive sur les alertes déjà ingérées
 * (ADR-011). Lecture et exécution : tout utilisateur authentifié (exécuter
 * une chasse est une action de lecture, pas une écriture métier).
 * Déclaration/modification/suppression : analystes et plus.
 */
@RestController
@RequestMapping("/api/v1/hunts")
@RequiredArgsConstructor
public class HuntController {

    private static final String WRITE_ROLES = "hasAnyRole('ADMIN','SOC_MANAGER','SOC_ANALYST')";

    private final HuntQueryService huntQueryService;
    private final HuntExecutionService huntExecutionService;
    private final HuntApiMapper mapper;

    @PostMapping
    @PreAuthorize(WRITE_ROLES)
    @ResponseStatus(HttpStatus.CREATED)
    public HuntResponse create(@Valid @RequestBody DeclareHuntRequest request) {
        HuntQuery declared = huntQueryService.declare(toCommand(request));
        return mapper.toResponse(declared);
    }

    @GetMapping
    public PageResponse<HuntResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {

        PageResult<HuntQuery> result = huntQueryService.search(
                new HuntQueryFilter(search, PageQuery.of(page, size)));
        return PageResponse.of(result, mapper::toResponse);
    }

    @GetMapping("/{id}")
    public HuntResponse get(@PathVariable UUID id) {
        return mapper.toResponse(huntQueryService.get(id));
    }

    @PatchMapping("/{id}")
    @PreAuthorize(WRITE_ROLES)
    public HuntResponse update(@PathVariable UUID id, @Valid @RequestBody DeclareHuntRequest request) {
        return mapper.toResponse(huntQueryService.update(id, toCommand(request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(WRITE_ROLES)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        huntQueryService.delete(id);
    }

    /** Exécute une requête sauvegardée — marque {@code lastExecutedAt} comme effet de bord. */
    @PostMapping("/{id}/execute")
    public HuntExecutionResponse execute(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {

        return mapper.toResponse(huntExecutionService.execute(id, PageQuery.of(page, size)));
    }

    /** Exécution ad hoc, sans sauvegarde — aucune écriture. */
    @PostMapping("/execute")
    public HuntExecutionResponse executeAdHoc(
            @Valid @RequestBody ExecuteAdHocRequest request,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {

        return mapper.toResponse(huntExecutionService.executeAdHoc(
                mapper.toDomain(request.criteria()), PageQuery.of(page, size)));
    }

    /** Catalogue des champs chassables et de leurs opérateurs — pilote le constructeur du frontend. */
    @GetMapping("/fields")
    public List<HuntFieldDescriptor> fields() {
        return mapper.fieldDescriptors();
    }

    private HuntQuery.DeclareCommand toCommand(DeclareHuntRequest request) {
        return HuntQuery.DeclareCommand.builder()
                .name(request.name())
                .description(request.description())
                .criteria(mapper.toDomain(request.criteria()))
                .visibility(request.visibility())
                .build();
    }
}
