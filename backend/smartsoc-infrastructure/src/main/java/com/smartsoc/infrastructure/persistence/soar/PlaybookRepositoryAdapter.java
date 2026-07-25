package com.smartsoc.infrastructure.persistence.soar;

import com.smartsoc.domain.common.PageResult;
import com.smartsoc.domain.soar.Playbook;
import com.smartsoc.domain.soar.PlaybookQuery;
import com.smartsoc.domain.soar.PlaybookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PlaybookRepositoryAdapter implements PlaybookRepository {

    private final SpringDataPlaybookRepository springDataRepository;
    private final PlaybookJpaMapper mapper;

    @Override
    public Playbook save(Playbook playbook) {
        return mapper.toDomain(springDataRepository.save(mapper.toJpa(playbook)));
    }

    @Override
    public Optional<Playbook> findById(UUID id) {
        return springDataRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public PageResult<Playbook> search(PlaybookQuery query) {
        Specification<PlaybookJpaEntity> spec = Specification.unrestricted();
        if (!query.includeArchived()) {
            spec = spec.and((root, q, cb) -> cb.isFalse(root.get("archived")));
        }
        if (query.search() != null && !query.search().isBlank()) {
            String pattern = "%" + query.search().trim().toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((root, q, cb) -> cb.or(
                    cb.like(cb.lower(root.get("name")), pattern),
                    cb.like(cb.lower(root.get("description")), pattern)));
        }

        PageRequest pageRequest = PageRequest.of(query.page().page(), query.page().size(),
                Sort.by(Sort.Direction.ASC, "name"));
        Page<PlaybookJpaEntity> page = springDataRepository.findAll(spec, pageRequest);

        return new PageResult<>(
                page.getContent().stream().map(mapper::toDomain).toList(),
                page.getTotalElements(), query.page().page(), query.page().size());
    }
}
