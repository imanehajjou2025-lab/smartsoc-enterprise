package com.smartsoc.api.investigations;

import com.smartsoc.api.investigations.dto.CaseDtos.CaseResponse;
import com.smartsoc.api.investigations.dto.CaseDtos.TaskResponse;
import com.smartsoc.api.investigations.dto.CaseDtos.TimelineEntryResponse;
import com.smartsoc.domain.investigations.Case;
import com.smartsoc.domain.investigations.CaseTask;
import com.smartsoc.domain.investigations.CaseTimelineEntry;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CaseApiMapper {

    CaseResponse toResponse(Case investigationCase);

    List<CaseResponse> toResponses(List<Case> cases);

    TaskResponse toTaskResponse(CaseTask task);

    List<TaskResponse> toTaskResponses(List<CaseTask> tasks);

    TimelineEntryResponse toTimelineResponse(CaseTimelineEntry entry);

    List<TimelineEntryResponse> toTimelineResponses(List<CaseTimelineEntry> entries);
}
