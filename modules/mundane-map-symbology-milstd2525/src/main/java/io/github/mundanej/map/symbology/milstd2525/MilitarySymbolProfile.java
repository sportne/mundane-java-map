package io.github.mundanej.map.symbology.milstd2525;

import java.util.Objects;
import java.util.Set;

/**
 * The finite MundaneJ supported MIL-STD-2525E Change 1 icon-based point-symbol profile.
 *
 * <p>This is not a complete MIL-STD-2525 implementation or conformance claim.
 */
public final class MilitarySymbolProfile {
    private static final int VERSION = 0x15;
    private static final int LAND_UNIT = 0x10;
    private static final int LAND_EQUIPMENT = 0x15;
    private static final int ACTIVITIES = 0x40;

    private static final Set<Integer> SYMBOL_SETS = Set.of(LAND_UNIT, LAND_EQUIPMENT, ACTIVITIES);
    private static final Set<Integer> LAND_UNIT_ENTITIES =
            Set.of(0x121100, 0x120500, 0x130300, 0x140700, 0x161300);
    private static final Set<Integer> LAND_EQUIPMENT_ENTITIES =
            Set.of(0x110100, 0x110200, 0x120200, 0x140200, 0x140800);
    private static final Set<Integer> ACTIVITY_ENTITIES =
            Set.of(0x120000, 0x131500, 0x140000, 0x170103, 0x170202);

    private static final Set<Integer> LAND_UNIT_SECTOR_ONE = Set.of(0x00, 0x25, 0x77);
    private static final Set<Integer> LAND_UNIT_SECTOR_TWO = Set.of(0x00, 0x02);
    private static final Set<Integer> LAND_EQUIPMENT_SECTOR_ONE = Set.of(0x00, 0x13);
    private static final Set<Integer> LAND_EQUIPMENT_SECTOR_TWO = Set.of(0x00, 0x06);
    private static final Set<Integer> ACTIVITY_SECTOR_ONE = Set.of(0x00, 0x17);
    private static final Set<Integer> ACTIVITY_SECTOR_TWO = Set.of(0x00, 0x04);

    private static final MilitarySymbolProfile STANDARD = new MilitarySymbolProfile();

    private MilitarySymbolProfile() {}

    /**
     * Returns the immutable approved profile.
     *
     * @return singleton bounded profile
     */
    public static MilitarySymbolProfile standard2525EChange1() {
        return STANDARD;
    }

    /**
     * Classifies one syntactically valid identifier without throwing for unsupported content.
     *
     * @param id identifier
     * @return stable support assessment
     */
    public MilitarySymbolAssessment assess(MilitarySymbolId id) {
        Objects.requireNonNull(id, "id");
        MilitarySymbolProblem earlyHardProblem = earlyHardProblem(id);
        if (earlyHardProblem != null) {
            return MilitarySymbolAssessment.problem(
                    MilitarySymbolSupport.UNSUPPORTED, earlyHardProblem);
        }

        MilitarySymbolProblem degradableProblem = null;
        MilitarySymbolSupport degradableSupport = null;
        if (!entities(id.symbolSet()).contains(id.entityCode())) {
            degradableProblem = problem(id, "MIL2525_ENTITY_UNSUPPORTED", "entity", 11, 16);
            degradableSupport = MilitarySymbolSupport.DEGRADED_ENTITY;
        } else if (!sectorOne(id.symbolSet()).contains(id.sectorOneModifier())) {
            degradableProblem =
                    problem(id, "MIL2525_MODIFIER_UNSUPPORTED", "sectorOneModifier", 17, 18);
            degradableSupport = MilitarySymbolSupport.DEGRADED_MODIFIER;
        } else if (!sectorTwo(id.symbolSet()).contains(id.sectorTwoModifier())) {
            degradableProblem =
                    problem(id, "MIL2525_MODIFIER_UNSUPPORTED", "sectorTwoModifier", 19, 20);
            degradableSupport = MilitarySymbolSupport.DEGRADED_MODIFIER;
        }

        MilitarySymbolProblem laterHardProblem = laterHardProblem(id);
        if (laterHardProblem != null) {
            return MilitarySymbolAssessment.problem(
                    MilitarySymbolSupport.UNSUPPORTED,
                    degradableProblem == null ? laterHardProblem : degradableProblem);
        }
        if (degradableProblem != null) {
            return MilitarySymbolAssessment.problem(degradableSupport, degradableProblem);
        }
        return MilitarySymbolAssessment.supported();
    }

    private static MilitarySymbolProblem earlyHardProblem(MilitarySymbolId id) {
        if (id.version() != VERSION) {
            return problem(id, "MIL2525_SIDC_VERSION", "version", 1, 2);
        }
        if (id.context() != 0) {
            return problem(id, "MIL2525_CONTEXT_UNSUPPORTED", "context", 3, 3);
        }
        if (id.standardIdentity() > 6) {
            return problem(id, "MIL2525_IDENTITY_UNSUPPORTED", "standardIdentity", 4, 4);
        }
        if (!SYMBOL_SETS.contains(id.symbolSet())) {
            return problem(id, "MIL2525_SYMBOL_SET_UNSUPPORTED", "symbolSet", 5, 6);
        }
        if (id.status() > 1) {
            return problem(id, "MIL2525_STATUS_UNSUPPORTED", "status", 7, 7);
        }
        if (id.headquartersTaskForceDummy() != 0) {
            return problem(
                    id,
                    "MIL2525_HQ_TASK_FORCE_DUMMY_UNSUPPORTED",
                    "headquartersTaskForceDummy",
                    8,
                    8);
        }
        if (id.amplifyingDescriptor() != 0) {
            return problem(
                    id, "MIL2525_AMPLIFYING_DESCRIPTOR_UNSUPPORTED", "amplifyingDescriptor", 9, 10);
        }
        return null;
    }

    private static MilitarySymbolProblem laterHardProblem(MilitarySymbolId id) {
        if (id.sectorOneCommonModifierSelector() != 0
                || id.sectorTwoCommonModifierSelector() != 0) {
            return problem(
                    id, "MIL2525_COMMON_MODIFIER_UNSUPPORTED", "commonModifierSelectors", 21, 22);
        }
        if (id.frameShape() != expectedFrame(id.symbolSet())) {
            return problem(id, "MIL2525_FRAME_SHAPE_MISMATCH", "frameShape", 23, 23);
        }
        if (id.reserved() != 0) {
            return problem(id, "MIL2525_RESERVED_NONZERO", "reserved", 24, 27);
        }
        if (id.countryOrEntityCode() != 0) {
            return problem(id, "MIL2525_COUNTRY_UNSUPPORTED", "countryOrEntityCode", 28, 30);
        }
        return null;
    }

    private static MilitarySymbolProblem problem(
            MilitarySymbolId id, String code, String field, int start, int end) {
        return new MilitarySymbolProblem(code, field, start, end, id.slice(start, end));
    }

    private static Set<Integer> entities(int symbolSet) {
        return switch (symbolSet) {
            case LAND_UNIT -> LAND_UNIT_ENTITIES;
            case LAND_EQUIPMENT -> LAND_EQUIPMENT_ENTITIES;
            case ACTIVITIES -> ACTIVITY_ENTITIES;
            default -> Set.of();
        };
    }

    private static Set<Integer> sectorOne(int symbolSet) {
        return switch (symbolSet) {
            case LAND_UNIT -> LAND_UNIT_SECTOR_ONE;
            case LAND_EQUIPMENT -> LAND_EQUIPMENT_SECTOR_ONE;
            case ACTIVITIES -> ACTIVITY_SECTOR_ONE;
            default -> Set.of();
        };
    }

    private static Set<Integer> sectorTwo(int symbolSet) {
        return switch (symbolSet) {
            case LAND_UNIT -> LAND_UNIT_SECTOR_TWO;
            case LAND_EQUIPMENT -> LAND_EQUIPMENT_SECTOR_TWO;
            case ACTIVITIES -> ACTIVITY_SECTOR_TWO;
            default -> Set.of();
        };
    }

    private static int expectedFrame(int symbolSet) {
        return switch (symbolSet) {
            case LAND_UNIT -> 3;
            case LAND_EQUIPMENT -> 4;
            case ACTIVITIES -> 8;
            default -> -1;
        };
    }
}
