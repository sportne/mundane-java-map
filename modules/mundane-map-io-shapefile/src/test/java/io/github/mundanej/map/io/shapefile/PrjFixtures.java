package io.github.mundanej.map.io.shapefile;

import java.nio.charset.StandardCharsets;

final class PrjFixtures {
    static final String EPSG_4326 =
            "GEOGCS[\"GCS_WGS_1984\",DATUM[\"D_WGS_1984\","
                    + "SPHEROID[\"WGS_1984\",6378137,298.257223563]],"
                    + "PRIMEM[\"Greenwich\",0],UNIT[\"Degree\",0.0174532925199433]]";
    static final String EPSG_3857 =
            "PROJCS[\"WGS_1984_Web_Mercator_Auxiliary_Sphere\","
                    + EPSG_4326
                    + ",PROJECTION[\"Mercator_Auxiliary_Sphere\"],"
                    + "PARAMETER[\"False_Easting\",0],PARAMETER[\"False_Northing\",0],"
                    + "PARAMETER[\"Central_Meridian\",0],"
                    + "PARAMETER[\"Standard_Parallel_1\",0],"
                    + "PARAMETER[\"Auxiliary_Sphere_Type\",0],UNIT[\"Meter\",1]]";

    private PrjFixtures() {}

    static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    static byte[] withBom(String value) {
        byte[] text = utf8(value);
        byte[] result = new byte[text.length + 3];
        result[0] = (byte) 0xef;
        result[1] = (byte) 0xbb;
        result[2] = (byte) 0xbf;
        System.arraycopy(text, 0, result, 3, text.length);
        return result;
    }

    static String nested(int depth) {
        return "A[".repeat(depth) + "\"x\"" + "]".repeat(depth);
    }

    static String tokenBoundary() {
        StringBuilder value = new StringBuilder(512);
        value.append("A[");
        for (int index = 0; index < 255; index++) {
            if (index > 0) {
                value.append(',');
            }
            value.append('0');
        }
        return value.append(']').toString();
    }

    static String characterBoundary(int length) {
        if (length < 5) {
            throw new IllegalArgumentException("length must be at least five");
        }
        return "A[\"" + "x".repeat(length - 5) + "\"]";
    }
}
