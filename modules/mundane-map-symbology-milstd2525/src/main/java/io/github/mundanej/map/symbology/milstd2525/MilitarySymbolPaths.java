package io.github.mundanej.map.symbology.milstd2525;

import io.github.mundanej.map.api.VectorPath;

/** Code-owned normalized vector geometry for the bounded profile. */
final class MilitarySymbolPaths {
    static final VectorPath FRIEND_FRAME =
            VectorPath.builder()
                    .moveTo(10, 20)
                    .lineTo(90, 20)
                    .lineTo(90, 80)
                    .lineTo(10, 80)
                    .close()
                    .build();
    static final VectorPath NEUTRAL_FRAME =
            VectorPath.builder()
                    .moveTo(20, 15)
                    .lineTo(80, 15)
                    .lineTo(80, 85)
                    .lineTo(20, 85)
                    .close()
                    .build();
    static final VectorPath HOSTILE_FRAME =
            VectorPath.builder()
                    .moveTo(50, 8)
                    .lineTo(92, 50)
                    .lineTo(50, 92)
                    .lineTo(8, 50)
                    .close()
                    .build();
    static final VectorPath UNKNOWN_FRAME =
            VectorPath.builder()
                    .moveTo(50, 8)
                    .cubicTo(68, 8, 70, 22, 72, 28)
                    .cubicTo(78, 30, 92, 32, 92, 50)
                    .cubicTo(92, 68, 78, 70, 72, 72)
                    .cubicTo(70, 78, 68, 92, 50, 92)
                    .cubicTo(32, 92, 30, 78, 28, 72)
                    .cubicTo(22, 70, 8, 68, 8, 50)
                    .cubicTo(8, 32, 22, 30, 28, 28)
                    .cubicTo(30, 22, 32, 8, 50, 8)
                    .close()
                    .build();
    static final VectorPath INFANTRY =
            VectorPath.builder()
                    .moveTo(31, 32)
                    .lineTo(69, 68)
                    .moveTo(69, 32)
                    .lineTo(31, 68)
                    .build();

    private MilitarySymbolPaths() {}

    static VectorPath frame(int identity) {
        return switch (identity) {
            case 0, 1 -> UNKNOWN_FRAME;
            case 2, 3 -> FRIEND_FRAME;
            case 4 -> NEUTRAL_FRAME;
            case 5, 6 -> HOSTILE_FRAME;
            default -> throw new IllegalArgumentException("unsupported standard identity");
        };
    }

    static VectorPath segmentedFrame(int identity) {
        return switch (identity) {
            case 0, 1 -> segmentedUnknown();
            case 2, 3 -> segmentedRectangle(10, 20, 90, 80);
            case 4 -> segmentedRectangle(20, 15, 80, 85);
            case 5, 6 -> segmentedDiamond();
            default -> throw new IllegalArgumentException("unsupported standard identity");
        };
    }

    private static VectorPath segmentedRectangle(
            double left, double top, double right, double bottom) {
        double horizontal = (right - left) * 0.36;
        double vertical = (bottom - top) * 0.36;
        return VectorPath.builder()
                .moveTo(left, top)
                .lineTo(left + horizontal, top)
                .moveTo(right - horizontal, top)
                .lineTo(right, top)
                .lineTo(right, top + vertical)
                .moveTo(right, bottom - vertical)
                .lineTo(right, bottom)
                .lineTo(right - horizontal, bottom)
                .moveTo(left + horizontal, bottom)
                .lineTo(left, bottom)
                .lineTo(left, bottom - vertical)
                .moveTo(left, top + vertical)
                .lineTo(left, top)
                .build();
    }

    private static VectorPath segmentedDiamond() {
        return VectorPath.builder()
                .moveTo(50, 8)
                .lineTo(65, 23)
                .moveTo(77, 35)
                .lineTo(92, 50)
                .lineTo(77, 65)
                .moveTo(65, 77)
                .lineTo(50, 92)
                .lineTo(35, 77)
                .moveTo(23, 65)
                .lineTo(8, 50)
                .lineTo(23, 35)
                .moveTo(35, 23)
                .lineTo(50, 8)
                .build();
    }

    private static VectorPath segmentedUnknown() {
        return VectorPath.builder()
                .moveTo(50, 8)
                .cubicTo(68, 8, 70, 22, 72, 28)
                .moveTo(78, 30)
                .cubicTo(92, 32, 92, 42, 92, 50)
                .moveTo(92, 50)
                .cubicTo(92, 68, 78, 70, 72, 72)
                .moveTo(70, 78)
                .cubicTo(68, 92, 58, 92, 50, 92)
                .moveTo(50, 92)
                .cubicTo(32, 92, 30, 78, 28, 72)
                .moveTo(22, 70)
                .cubicTo(8, 68, 8, 58, 8, 50)
                .moveTo(8, 50)
                .cubicTo(8, 32, 22, 30, 28, 28)
                .moveTo(30, 22)
                .cubicTo(32, 8, 42, 8, 50, 8)
                .build();
    }
}
