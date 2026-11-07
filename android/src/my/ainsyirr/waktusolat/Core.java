package my.ainsyirr.waktusolat;

/** Pure scheduling logic (no Android imports) so it can be unit-tested on a desktop JVM. */
final class Core {
    static final String[] KEYS = {"fajr", "dhuhr", "asr", "maghrib", "isha"};
    static final String[] NAMES = {"Subuh", "Zohor", "Asar", "Maghrib", "Isyak"};
    static final int AZAN = 0, REMIND = 1;

    private Core() {}

    /**
     * Pick the next alarm.
     * @param now      current time, ms
     * @param after    never pick anything at or before this (last fired alarm), ms
     * @param days     per consecutive day: epoch SECONDS for fajr, dhuhr, asr, maghrib, isha (null = no data)
     * @param on       azan enabled per prayer
     * @param remindMin minutes before each azan for a reminder (0 = none)
     * @return {alarmAtMs, prayerMs, prayerIndex, type, dayIndex} or null if nothing upcoming
     */
    static long[] pick(long now, long after, long[][] days, boolean[] on, int remindMin) {
        long floor = Math.max(now, after);
        long[] best = null;
        for (int d = 0; d < days.length; d++) {
            if (days[d] == null) continue;
            for (int i = 0; i < 5; i++) {
                if (!on[i]) continue;
                long t = days[d][i] * 1000L;
                if (t > floor && (best == null || t < best[0])) best = new long[]{t, t, i, AZAN, d};
                if (remindMin > 0) {
                    long r = t - remindMin * 60000L;
                    if (r > floor && (best == null || r < best[0])) best = new long[]{r, t, i, REMIND, d};
                }
            }
        }
        return best;
    }

    static String label(int i, boolean friday) { return i == 1 && friday ? "Jumaat" : NAMES[i]; }
}
