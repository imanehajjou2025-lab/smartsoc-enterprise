package com.smartsoc.api.intelligence;

import com.smartsoc.api.intelligence.dto.IocDtos.IocResponse;
import com.smartsoc.domain.intelligence.Indicator;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.time.Instant;

/**
 * Le statut n'est pas une propriété stockée de l'indicateur : il se
 * déduit de sa fenêtre de validité. L'instant d'évaluation est donc un
 * PARAMÈTRE explicite, que l'appelant fixe une seule fois — la liste
 * renvoyée et les statuts qu'elle affiche parlent ainsi du même moment.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface IocApiMapper {

    @Mapping(target = "status", expression = "java(indicator.statusAt(evaluatedAt))")
    IocResponse toResponse(Indicator indicator, Instant evaluatedAt);
}
