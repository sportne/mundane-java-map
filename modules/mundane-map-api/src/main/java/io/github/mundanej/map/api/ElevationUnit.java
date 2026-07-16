package io.github.mundanej.map.api;

/** Declared vertical unit for elevation samples. */
public enum ElevationUnit {
    /** SI metre. */
    METRE(1.0),

    /** International foot, exactly 0.3048 metres. */
    INTERNATIONAL_FOOT(0.3048),

    /** United States survey foot, exactly 1200/3937 metres. */
    US_SURVEY_FOOT(1200.0 / 3937.0);

    private final double metresPerUnit;

    ElevationUnit(double metresPerUnit) {
        this.metresPerUnit = metresPerUnit;
    }

    /**
     * Returns the fixed conversion factor from this unit to metres.
     *
     * @return metres represented by one declared unit
     */
    public double metresPerUnit() {
        return metresPerUnit;
    }
}
