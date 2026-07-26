package io.github.mundanej.map.io.geopackage;

record GeoPackageTileMatrix(
        int zoom, int matrixWidth, int matrixHeight, double pixelXSize, double pixelYSize) {}
