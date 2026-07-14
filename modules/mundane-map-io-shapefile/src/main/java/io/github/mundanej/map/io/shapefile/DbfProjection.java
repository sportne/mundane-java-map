package io.github.mundanej.map.io.shapefile;

/* Cursor-confined packed DBF projection in physical-field order. */
final class DbfProjection {
    private final int[] physicalFields;
    private final int[] outputPositions;
    private final String[] outputNames;
    private final int width;

    DbfProjection(int[] physicalFields, int[] outputPositions, String[] outputNames, int width) {
        this.physicalFields = physicalFields;
        this.outputPositions = outputPositions;
        this.outputNames = outputNames;
        this.width = width;
    }

    int[] physicalFields() {
        return physicalFields;
    }

    int[] outputPositions() {
        return outputPositions;
    }

    String[] outputNames() {
        return outputNames;
    }

    int width() {
        return width;
    }

    int size() {
        return physicalFields.length;
    }
}
