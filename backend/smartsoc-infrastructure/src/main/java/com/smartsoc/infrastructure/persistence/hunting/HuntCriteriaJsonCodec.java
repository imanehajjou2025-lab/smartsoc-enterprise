package com.smartsoc.infrastructure.persistence.hunting;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartsoc.domain.hunting.HuntCondition;
import com.smartsoc.domain.hunting.HuntField;
import com.smartsoc.domain.hunting.HuntGroup;
import com.smartsoc.domain.hunting.HuntLogicalOperator;
import com.smartsoc.domain.hunting.HuntNode;
import com.smartsoc.domain.hunting.HuntOperator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Conversion explicite {@code HuntGroup} ↔ JSON — pas d'annotations Jackson
 * sur le domaine (qui doit rester framework-free, ADR-002), donc pas de
 * polymorphisme automatique sur {@link HuntNode}. Une marche récursive
 * écrite à la main, testable seule, sans risque de dépendre d'une
 * configuration Jackson subtile (mixins, détection des records).
 *
 * <p>Nommage volontairement générique ({@code toJson}/{@code fromJson}
 * prenant/rendant {@code HuntGroup}↔{@code String}) : MapStruct
 * ({@link HuntQueryJpaMapper}, {@code uses = HuntCriteriaJsonCodec.class})
 * les sélectionne automatiquement pour le champ {@code criteria}.
 */
@Component
@RequiredArgsConstructor
public class HuntCriteriaJsonCodec {

    private static final String KIND = "kind";
    private static final String CONDITION = "CONDITION";
    private static final String GROUP = "GROUP";

    private final ObjectMapper objectMapper;

    public String toJson(HuntGroup group) {
        return toJsonNode(group).toString();
    }

    public HuntGroup fromJson(String json) {
        try {
            return (HuntGroup) toNode(objectMapper.readTree(json));
        } catch (JsonProcessingException e) {
            // Ce JSON est le nôtre (produit par toJson) : une erreur ici
            // signale une corruption de la base, pas une entrée invalide.
            throw new IllegalStateException("Corrupted hunt criteria JSON: " + json, e);
        }
    }

    private JsonNode toJsonNode(HuntNode node) {
        ObjectNode object = objectMapper.createObjectNode();
        switch (node) {
            case HuntCondition condition -> {
                object.put(KIND, CONDITION);
                object.put("field", condition.field().name());
                object.put("operator", condition.operator().name());
                object.put("value", condition.value());
            }
            case HuntGroup group -> {
                object.put(KIND, GROUP);
                object.put("operator", group.operator().name());
                ArrayNode children = object.putArray("children");
                group.children().forEach(child -> children.add(toJsonNode(child)));
            }
        }
        return object;
    }

    private HuntNode toNode(JsonNode json) {
        String kind = json.get(KIND).asText();
        return switch (kind) {
            case CONDITION -> new HuntCondition(
                    HuntField.valueOf(json.get("field").asText()),
                    HuntOperator.valueOf(json.get("operator").asText()),
                    json.get("value").asText());
            case GROUP -> new HuntGroup(
                    HuntLogicalOperator.valueOf(json.get("operator").asText()),
                    childrenOf(json.get("children")));
            default -> throw new IllegalStateException("Unknown hunt criteria node kind: " + kind);
        };
    }

    private List<HuntNode> childrenOf(JsonNode children) {
        List<HuntNode> nodes = new ArrayList<>();
        children.forEach(child -> nodes.add(toNode(child)));
        return nodes;
    }
}
