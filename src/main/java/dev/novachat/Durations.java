package dev.novachat;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Duraciones tipo "1h30m", "2d", "perm". */
final class Durations {

    private static final Pattern FULL = Pattern.compile("(?:\\d{1,6}[smhdw])+");
    private static final Pattern PART = Pattern.compile("(\\d{1,6})([smhdw])");
    private static final long MAX = 10L * 365 * 86_400_000L;

    private Durations() {
    }

    /** @return milisegundos; 0 = permanente; -1 = no es una duración. */
    static long parse(String input) {
        String s = input.toLowerCase(Locale.ROOT);
        if (s.equals("perm") || s.equals("permanent") || s.equals("permanente") || s.equals("forever")) return 0;
        if (!FULL.matcher(s).matches()) return -1;
        Matcher m = PART.matcher(s);
        long total = 0;
        while (m.find()) {
            long n = Long.parseLong(m.group(1));
            long unit = switch (m.group(2)) {
                case "s" -> 1_000L;
                case "m" -> 60_000L;
                case "h" -> 3_600_000L;
                case "d" -> 86_400_000L;
                default -> 604_800_000L;
            };
            total += n * unit;
        }
        return total <= 0 ? -1 : Math.min(total, MAX);
    }

    static String format(long ms) {
        long s = Math.max(0, ms / 1000);
        long d = s / 86_400;
        s %= 86_400;
        long h = s / 3_600;
        s %= 3_600;
        long m = s / 60;
        s %= 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(d).append("d ");
        if (h > 0) sb.append(h).append("h ");
        if (m > 0) sb.append(m).append("m ");
        if (s > 0 || sb.length() == 0) sb.append(s).append("s");
        return sb.toString().trim();
    }
}
