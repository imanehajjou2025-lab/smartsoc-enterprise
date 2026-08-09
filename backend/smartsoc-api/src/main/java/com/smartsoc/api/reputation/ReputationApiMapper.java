package com.smartsoc.api.reputation;

import com.smartsoc.api.reputation.dto.ReputationDtos.ReputationResponse;
import com.smartsoc.domain.reputation.ObservableReputation;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ReputationApiMapper {

    ReputationResponse toResponse(ObservableReputation reputation);
}
