package com.smartsoc.api.assets;

import com.smartsoc.api.assets.dto.AssetDtos.AssetResponse;
import com.smartsoc.domain.assets.Asset;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface AssetApiMapper {

    AssetResponse toResponse(Asset asset);

    List<AssetResponse> toResponses(List<Asset> assets);
}
