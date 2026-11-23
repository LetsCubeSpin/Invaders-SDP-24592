package vfx;

import java.awt.Color;

import entity.Boss.BossType;

/** Boss silhouettes on the original 16x7 grid, rendered at 2x in game. */
public final class BossSprites {

    /** Broad armored hull with two heavy cannons. */
    private static final Color[][] ARMORED = parse(BossType.BOSS_A,
            "....HHHHHHHH....",
            "..BBBBBBBBBBBB..",
            ".BBBSSBHHBSSBBB.",
            "BBBBBBBBBBBBBBBB",
            "BBSSBBBBBBBBSSBB",
            ".BBBSS....SSBBB.",
            "..BB........BB..");

    /** Swept wings and a pointed nose for the faster boss. */
    private static final Color[][] INTERCEPTOR = parse(BossType.BOSS_B,
            "B......HH......B",
            "BB.....BB.....BB",
            ".BB...BBBB...BB.",
            "..BBBBBBBBBBBB..",
            "...SSBBHHBBSS...",
            ".....BBBBBB.....",
            ".......BB.......");

    /** Vespera's forked wings surround two recessed launch bays. */
    private static final Color[][] CARRIER = parse(BossType.BOSS_C,
            "BB....B..B....BB",
            "BBB...BHHB...BBB",
            "BBBBBBBBBBBBBBBB",
            ".BBSSBBHHBBSSBB.",
            "..BBSSBBBBSSBB..",
            ".BBB..BBBB..BBB.",
            "B..B...BB...B..B");

    /** Obelisk's tall central core flanked by shield-projector pylons. */
    private static final Color[][] OBELISK = parse(BossType.BOSS_D,
            ".......HH.......",
            "......HBBH......",
            "..BB.BBHHBB.BB..",
            ".BBBBBBHHBBBBBB.",
            "BBBBSSBHHBSSBBBB",
            "..BB.BBSSBB.BB..",
            "......BBBB......");

    private BossSprites() {
    }

    /** Returns pixel colors indexed [x][y], with null for transparent cells. */
    public static Color[][] forType(final BossType type) {
        switch (type) {
        case BOSS_A:
            return ARMORED;
        case BOSS_B:
            return INTERCEPTOR;
        case BOSS_C:
            return CARRIER;
        case BOSS_D:
            return OBELISK;
        default:
            throw new IllegalArgumentException("Unknown boss type: " + type);
        }
    }

    private static Color[][] parse(final BossType type, final String... rows) {
        Color base = type.getColor();
        Color shade = base.darker();
        Color[][] pixels = new Color[rows[0].length()][rows.length];
        for (int y = 0; y < rows.length; y++) {
            for (int x = 0; x < rows[y].length(); x++) {
                switch (rows[y].charAt(x)) {
                case 'B':
                    pixels[x][y] = base;
                    break;
                case 'S':
                    pixels[x][y] = shade;
                    break;
                case 'H':
                    pixels[x][y] = Color.WHITE;
                    break;
                default:
                    break;
                }
            }
        }
        return pixels;
    }
}
