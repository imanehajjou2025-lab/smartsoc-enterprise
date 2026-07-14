package com.smartsoc.api.incidents;

import com.smartsoc.api.incidents.dto.IncidentDtos.IncidentResponse;
import com.smartsoc.api.incidents.dto.IncidentDtos.TimelineEntryResponse;
import com.smartsoc.domain.incidents.Incident;
import com.smartsoc.domain.incidents.IncidentTimelineEntry;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface IncidentApiMapper {

    IncidentResponse toResponse(Incident incident);

    List<IncidentResponse> toResponses(List<Incident> incidents);

    TimelineEntryResponse toTimelineResponse(IncidentTimelineEntry entry);

    List<TimelineEntryResponse> toTimelineResponses(List<IncidentTimelineEntry> entries);
}
