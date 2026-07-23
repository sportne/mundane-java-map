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
    private static final VectorPath ARMOR =
            VectorPath.builder()
                    .moveTo(25, 50)
                    .cubicTo(25, 35, 75, 35, 75, 50)
                    .cubicTo(75, 65, 25, 65, 25, 50)
                    .close()
                    .build();
    private static final VectorPath ARTILLERY =
            VectorPath.builder()
                    .moveTo(50, 34)
                    .cubicTo(70, 34, 70, 66, 50, 66)
                    .cubicTo(30, 66, 30, 34, 50, 34)
                    .close()
                    .moveTo(50, 38)
                    .lineTo(50, 62)
                    .build();
    private static final VectorPath ENGINEER =
            VectorPath.builder()
                    .moveTo(30, 34)
                    .lineTo(30, 66)
                    .moveTo(30, 34)
                    .lineTo(68, 34)
                    .moveTo(30, 50)
                    .lineTo(62, 50)
                    .moveTo(30, 66)
                    .lineTo(68, 66)
                    .build();
    private static final VectorPath MEDICAL =
            VectorPath.builder()
                    .moveTo(43, 30)
                    .lineTo(57, 30)
                    .lineTo(57, 43)
                    .lineTo(70, 43)
                    .lineTo(70, 57)
                    .lineTo(57, 57)
                    .lineTo(57, 70)
                    .lineTo(43, 70)
                    .lineTo(43, 57)
                    .lineTo(30, 57)
                    .lineTo(30, 43)
                    .lineTo(43, 43)
                    .close()
                    .build();
    private static final VectorPath RIFLE =
            VectorPath.builder()
                    .moveTo(28, 66)
                    .lineTo(68, 30)
                    .lineTo(74, 37)
                    .moveTo(34, 60)
                    .lineTo(44, 70)
                    .moveTo(58, 40)
                    .lineTo(66, 48)
                    .build();
    private static final VectorPath MACHINE_GUN =
            VectorPath.builder()
                    .moveTo(25, 43)
                    .lineTo(72, 43)
                    .moveTo(50, 43)
                    .lineTo(35, 70)
                    .moveTo(50, 43)
                    .lineTo(65, 70)
                    .moveTo(68, 38)
                    .lineTo(76, 48)
                    .build();
    private static final VectorPath TANK =
            VectorPath.builder()
                    .moveTo(24, 42)
                    .lineTo(76, 42)
                    .lineTo(76, 64)
                    .lineTo(24, 64)
                    .close()
                    .moveTo(42, 42)
                    .lineTo(42, 33)
                    .lineTo(60, 33)
                    .lineTo(60, 42)
                    .moveTo(60, 36)
                    .lineTo(78, 30)
                    .build();
    private static final VectorPath MEDICAL_EQUIPMENT =
            VectorPath.builder()
                    .moveTo(28, 28)
                    .lineTo(72, 28)
                    .lineTo(72, 72)
                    .lineTo(28, 72)
                    .close()
                    .moveTo(46, 36)
                    .lineTo(54, 36)
                    .lineTo(54, 46)
                    .lineTo(64, 46)
                    .lineTo(64, 54)
                    .lineTo(54, 54)
                    .lineTo(54, 64)
                    .lineTo(46, 64)
                    .lineTo(46, 54)
                    .lineTo(36, 54)
                    .lineTo(36, 46)
                    .lineTo(46, 46)
                    .close()
                    .build();
    private static final VectorPath TRUCK =
            VectorPath.builder()
                    .moveTo(22, 38)
                    .lineTo(62, 38)
                    .lineTo(62, 47)
                    .lineTo(76, 47)
                    .lineTo(76, 64)
                    .lineTo(22, 64)
                    .close()
                    .moveTo(34, 64)
                    .cubicTo(42, 64, 42, 76, 34, 76)
                    .cubicTo(26, 76, 26, 64, 34, 64)
                    .close()
                    .moveTo(66, 64)
                    .cubicTo(74, 64, 74, 76, 66, 76)
                    .cubicTo(58, 76, 58, 64, 66, 64)
                    .close()
                    .build();
    private static final VectorPath CIVIL_DISTURBANCE =
            VectorPath.builder()
                    .moveTo(35, 35)
                    .cubicTo(43, 35, 43, 47, 35, 47)
                    .cubicTo(27, 47, 27, 35, 35, 35)
                    .close()
                    .moveTo(65, 35)
                    .cubicTo(73, 35, 73, 47, 65, 47)
                    .cubicTo(57, 47, 57, 35, 65, 35)
                    .close()
                    .moveTo(25, 67)
                    .cubicTo(30, 48, 45, 48, 50, 67)
                    .moveTo(50, 67)
                    .cubicTo(55, 48, 70, 48, 75, 67)
                    .build();
    private static final VectorPath LAW_ENFORCEMENT = star(50, 50, 22, 10, 5);
    private static final VectorPath FIRE =
            VectorPath.builder()
                    .moveTo(50, 22)
                    .cubicTo(72, 43, 70, 70, 50, 76)
                    .cubicTo(28, 70, 30, 46, 42, 36)
                    .cubicTo(43, 47, 51, 46, 50, 22)
                    .close()
                    .build();
    private static final VectorPath EARTHQUAKE =
            VectorPath.builder()
                    .moveTo(25, 42)
                    .lineTo(42, 42)
                    .lineTo(47, 28)
                    .lineTo(55, 65)
                    .lineTo(62, 47)
                    .lineTo(75, 47)
                    .build();
    private static final VectorPath FLOOD =
            VectorPath.builder()
                    .moveTo(22, 39)
                    .cubicTo(32, 31, 42, 47, 52, 39)
                    .cubicTo(62, 31, 70, 47, 78, 39)
                    .moveTo(22, 55)
                    .cubicTo(32, 47, 42, 63, 52, 55)
                    .cubicTo(62, 47, 70, 63, 78, 55)
                    .moveTo(22, 71)
                    .cubicTo(32, 63, 42, 79, 52, 71)
                    .cubicTo(62, 63, 70, 79, 78, 71)
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

    static VectorPath entity(int symbolSet, int entityCode) {
        return switch (symbolSet) {
            case 0x10 ->
                    switch (entityCode) {
                        case 0x121100 -> INFANTRY;
                        case 0x120500 -> ARMOR;
                        case 0x130300 -> ARTILLERY;
                        case 0x140700 -> ENGINEER;
                        case 0x161300 -> MEDICAL;
                        default -> null;
                    };
            case 0x15 ->
                    switch (entityCode) {
                        case 0x110100 -> RIFLE;
                        case 0x110200 -> MACHINE_GUN;
                        case 0x120200 -> TANK;
                        case 0x140200 -> MEDICAL_EQUIPMENT;
                        case 0x140800 -> TRUCK;
                        default -> null;
                    };
            case 0x40 ->
                    switch (entityCode) {
                        case 0x120000 -> CIVIL_DISTURBANCE;
                        case 0x131500 -> LAW_ENFORCEMENT;
                        case 0x140000 -> FIRE;
                        case 0x170103 -> EARTHQUAKE;
                        case 0x170202 -> FLOOD;
                        default -> null;
                    };
            default -> null;
        };
    }

    static VectorPath sectorOne(int symbolSet, int modifier) {
        if (modifier == 0) {
            return null;
        }
        return switch ((symbolSet << 8) | modifier) {
            case 0x1025 -> glyphF();
            case 0x1077 -> plusAt(77, 28, 8);
            case 0x1513 -> chevron(72, 50);
            case 0x4017 -> exclamation();
            default -> null;
        };
    }

    static VectorPath sectorTwo(int symbolSet, int modifier) {
        if (modifier == 0) {
            return null;
        }
        return switch ((symbolSet << 8) | modifier) {
            case 0x1002 -> snowflake();
            case 0x1506 -> trailer();
            case 0x4004 -> meeting();
            default -> null;
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

    private static VectorPath star(double cx, double cy, double outer, double inner, int points) {
        VectorPath.Builder builder = VectorPath.builder();
        for (int index = 0; index < points * 2; index++) {
            double angle = -Math.PI / 2 + index * Math.PI / points;
            double radius = index % 2 == 0 ? outer : inner;
            double x = cx + Math.cos(angle) * radius;
            double y = cy + Math.sin(angle) * radius;
            if (index == 0) {
                builder.moveTo(x, y);
            } else {
                builder.lineTo(x, y);
            }
        }
        return builder.close().build();
    }

    private static VectorPath glyphF() {
        return VectorPath.builder()
                .moveTo(73, 18)
                .lineTo(73, 38)
                .moveTo(73, 18)
                .lineTo(86, 18)
                .moveTo(73, 27)
                .lineTo(83, 27)
                .build();
    }

    private static VectorPath plusAt(double x, double y, double radius) {
        return VectorPath.builder()
                .moveTo(x - radius, y)
                .lineTo(x + radius, y)
                .moveTo(x, y - radius)
                .lineTo(x, y + radius)
                .build();
    }

    private static VectorPath chevron(double x, double y) {
        return VectorPath.builder()
                .moveTo(x - 12, y - 12)
                .lineTo(x, y)
                .lineTo(x - 12, y + 12)
                .build();
    }

    private static VectorPath exclamation() {
        return VectorPath.builder()
                .moveTo(78, 18)
                .lineTo(78, 32)
                .moveTo(78, 36)
                .lineTo(78, 38)
                .build();
    }

    private static VectorPath snowflake() {
        return VectorPath.builder()
                .moveTo(70, 70)
                .lineTo(88, 88)
                .moveTo(88, 70)
                .lineTo(70, 88)
                .moveTo(79, 67)
                .lineTo(79, 91)
                .build();
    }

    private static VectorPath trailer() {
        return VectorPath.builder()
                .moveTo(68, 68)
                .lineTo(88, 68)
                .lineTo(88, 82)
                .lineTo(68, 82)
                .close()
                .moveTo(68, 75)
                .lineTo(62, 75)
                .build();
    }

    private static VectorPath meeting() {
        return VectorPath.builder()
                .moveTo(71, 72)
                .cubicTo(77, 72, 77, 82, 71, 82)
                .cubicTo(65, 82, 65, 72, 71, 72)
                .close()
                .moveTo(86, 72)
                .cubicTo(92, 72, 92, 82, 86, 82)
                .cubicTo(80, 82, 80, 72, 86, 72)
                .close()
                .build();
    }
}
