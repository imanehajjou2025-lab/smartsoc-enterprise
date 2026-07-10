package com.smartsoc.infrastructure.persistence.identity;

import com.smartsoc.domain.identity.User;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * Converts between the framework-free domain entity and its JPA mapping.
 * Audit and soft-delete columns are infrastructure-only and deliberately
 * not exposed to the domain (hence the ignored unmapped targets).
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserJpaMapper {

    UserJpaEntity toJpa(User user);

    User toDomain(UserJpaEntity entity);
}
