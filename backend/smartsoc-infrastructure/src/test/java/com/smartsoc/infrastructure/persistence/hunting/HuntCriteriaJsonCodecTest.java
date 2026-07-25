package com.smartsoc.infrastructure.persistence.hunting;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartsoc.domain.hunting.HuntCondition;
import com.smartsoc.domain.hunting.HuntField;
import com.smartsoc.domain.hunting.HuntGroup;
import com.smartsoc.domain.hunting.HuntLogicalOperator;
import com.smartsoc.domain.hunting.HuntOperator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Aller-retour du codec — y compris sur un arbre IMBRIQUÉ (OR sous un AND),
 * hors de la portée V1 de {@code HuntQuery} mais que le codec doit
 * transporter fidèlement puisque {@code HuntNode} le permet déjà.
 */
class HuntCriteriaJsonCodecTest {

    private final HuntCriteriaJsonCodec codec = new HuntCriteriaJsonCodec(new ObjectMapper());

    @Test
    void roundTripsAFlatAndGroup() {
        HuntGroup original = new HuntGroup(HuntLogicalOperator.AND, List.of(
                new HuntCondition(HuntField.SEVERITY, HuntOperator.EQUALS, "CRITICAL"),
                new HuntCondition(HuntField.RAW_PAYLOAD_TEXT, HuntOperator.CONTAINS, "mimikatz")));

        HuntGroup restored = codec.fromJson(codec.toJson(original));

        assertThat(restored).isEqualTo(original);
    }

    @Test
    void roundTripsANestedGroup() {
        HuntGroup nested = new HuntGroup(HuntLogicalOperator.AND, List.of(
                new HuntGroup(HuntLogicalOperator.OR, List.of(
                        new HuntCondition(HuntField.HOSTNAME, HuntOperator.EQUALS, "srv-01"),
                        new HuntCondition(HuntField.HOSTNAME, HuntOperator.EQUALS, "srv-02"))),
                new HuntCondition(HuntField.SEVERITY, HuntOperator.EQUALS, "HIGH")));

        HuntGroup restored = codec.fromJson(codec.toJson(nested));

        assertThat(restored).isEqualTo(nested);
        assertThat(restored.children().getFirst()).isInstanceOf(HuntGroup.class);
    }

    @Test
    void jsonShapeCarriesAnExplicitKindDiscriminant() {
        HuntGroup group = new HuntGroup(HuntLogicalOperator.AND,
                List.of(new HuntCondition(HuntField.STATUS, HuntOperator.EQUALS, "NEW")));

        String json = codec.toJson(group);

        assertThat(json).contains("\"kind\":\"GROUP\"").contains("\"kind\":\"CONDITION\"");
    }
}
