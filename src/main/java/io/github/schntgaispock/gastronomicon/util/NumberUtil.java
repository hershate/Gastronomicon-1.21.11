package io.github.schntgaispock.gastronomicon.util;

import java.util.concurrent.ThreadLocalRandom;

import lombok.Getter;
import lombok.experimental.UtilityClass;

@UtilityClass
public class NumberUtil {

    private static @Getter ThreadLocalRandom random = ThreadLocalRandom.current();

    public static int clamp(int x, int lowerBound, int upperBound) {
        return Math.min(Math.max(x, lowerBound), upperBound);
    }

    public static double clamp(double x, double lowerBound, double upperBound) {
        return Math.min(Math.max(x, lowerBound), upperBound);
    }

    // [perf] 罗马数字查表常量：原实现每次调用分配 4 个 String[]。这些表恒定，提为 static final。
    private static final String[] RN_THOUSANDS = { "", "M", "MM", "MMM" };
    private static final String[] RN_HUNDREDS =
        { "", "C", "CC", "CCC", "CD", "D", "DC", "DCC", "DCCC", "CM" };
    private static final String[] RN_TENS =
        { "", "X", "XX", "XXX", "XL", "L", "LX", "LXX", "LXXX", "XC" };
    private static final String[] RN_ONES =
        { "", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX" };

    public static String asRomanNumeral(int x) {
        if (x >= 4000 || x <= 0)
            return Integer.toString(x);
        return RN_THOUSANDS[x / 1000] + RN_HUNDREDS[(x / 100) % 10]
            + RN_TENS[(x / 10) % 10] + RN_ONES[x % 10];
    }

    public static boolean flip(double chance) {
        return random.nextDouble(1) < chance;
    }

    public static int randomRound(double x) {
        final int f = (int) Math.floor(x);
        final int c = (int) Math.ceil(x);
        if (f == c)
            return f;

        return flip(x - f) ? f : c;
    }

    public static double roundToPrecision(double x, int precision) {
        final double magn = pow10(precision);
        return Math.round(x * magn) / magn;
    }

    /**
     * [perf] 10 的幂：常用 precision 为 0..4，用 switch 常量取代 Math.pow（后者约 20-40ns/次）。
     * 异常 precision 回退 Math.pow，行为不变。本方法用于 FoodEffect/FoodItemStack 描述构造。
     */
    private static double pow10(int precision) {
        return switch (precision) {
            case 0 -> 1.0;
            case 1 -> 10.0;
            case 2 -> 100.0;
            case 3 -> 1_000.0;
            case 4 -> 10_000.0;
            case 5 -> 100_000.0;
            case 6 -> 1_000_000.0;
            default -> Math.pow(10, precision);
        };
    }

    public static double roundToPercent(double x, int precision) {
        return roundToPrecision(x * 100, precision);
    }

    public static int getFortuneAmount(int fortuneLevel, int sickleTier, int baseAmount) {
        return getFortuneAmount(fortuneLevel + sickleTier, baseAmount);
    }

    public static int getFortuneAmount(int fortuneLevel, int baseAmount) {
        return baseAmount + ThreadLocalRandom.current().nextInt(fortuneLevel, 1 + (int) Math.ceil(fortuneLevel * 1.5));
    }

}
