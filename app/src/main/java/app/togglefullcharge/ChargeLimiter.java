package app.togglefullcharge;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.UserManager;
import android.provider.Settings;

import java.util.Calendar;

/**
 * 삼성 One UI '배터리 보호' 설정(Settings.Global)을 읽고 쓰는 핵심 로직.
 *
 * protect_battery 값 (One UI 6.1 ~ 9 기준)
 *   0 = 꺼짐, 1 = 최대(battery_protection_threshold %에서 정지), 3 = 기본, 4 = 수면 중 배터리 보호
 */
public final class ChargeLimiter {

    public static final String KEY_MODE = "protect_battery";
    public static final String KEY_THRESHOLD = "battery_protection_threshold";
    public static final int MODE_OFF = 0;
    public static final int MODE_UNSUPPORTED = -1;

    private static final String PREFS = "boost_state";
    private static final String P_ACTIVE = "active";
    private static final String P_SAVED_MODE = "saved_mode";
    private static final String P_END_AT = "end_at";
    private static final String P_HOURS = "hours";
    private static final String P_UNPLUG = "restore_on_unplug";
    private static final String P_KEEP_FULL = "keep_after_full";
    private static final String P_SCHEDULE = "schedule";
    private static final String P_SCHEDULE_DAYS = "schedule_days";
    private static final String P_SCHEDULE_MINUTE = "schedule_minute";

    public enum StartResult { STARTED, NO_PERMISSION, UNSUPPORTED, ALREADY_UNLIMITED, LIMIT_FULL, ALREADY_ACTIVE, FAILED }

    private ChargeLimiter() {}

    // ---------- 조회 ----------

    public static boolean hasPermission(Context ctx) {
        return ctx.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static int readMode(Context ctx) {
        return Settings.Global.getInt(ctx.getContentResolver(), KEY_MODE, MODE_UNSUPPORTED);
    }

    public static int readThreshold(Context ctx) {
        return Settings.Global.getInt(ctx.getContentResolver(), KEY_THRESHOLD, -1);
    }

    public static String describeMode(Context ctx, int mode) {
        switch (mode) {
            case MODE_UNSUPPORTED: return ctx.getString(R.string.mode_unsupported);
            case 0: return ctx.getString(R.string.mode_off);
            case 1:
                int t = readThreshold(ctx);
                return ctx.getString(R.string.mode_max, t > 0 ? t : 80);
            case 3: return ctx.getString(R.string.mode_basic);
            case 4: return ctx.getString(R.string.mode_sleep);
            default: return ctx.getString(R.string.mode_unknown, mode);
        }
    }

    /** 최대 모드의 한도가 100%라 제한을 풀어도 달라지는 것이 없는 상태 */
    public static boolean isLimitFull(Context ctx, int mode) {
        return limitPercent(ctx, mode) >= 100;
    }

    /** 위젯에 표시할 제한 퍼센트. 최대 모드가 아니면 -1 */
    public static int limitPercent(Context ctx, int mode) {
        if (mode != 1) return -1;
        int t = readThreshold(ctx);
        return t > 0 ? t : 80;
    }

    // ---------- 상태 저장 ----------

    private static boolean migrated = false;

    /** 재부팅 후 잠금 해제 전에도 읽을 수 있도록 기기 보호 저장소에 둠 */
    private static SharedPreferences prefs(Context ctx) {
        Context dp = ctx.createDeviceProtectedStorageContext();
        // 1.4.3 이하에서 쓰던 일반 저장소의 값을 한 번 옮김 (일반 저장소는 잠금 해제 후에만 읽을 수 있음)
        if (!migrated && ctx.getSystemService(UserManager.class).isUserUnlocked()) {
            dp.moveSharedPreferencesFrom(ctx, PREFS);
            migrated = true;
        }
        return dp.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static void listen(Context ctx, SharedPreferences.OnSharedPreferenceChangeListener l) {
        prefs(ctx).registerOnSharedPreferenceChangeListener(l);
    }

    public static void unlisten(Context ctx, SharedPreferences.OnSharedPreferenceChangeListener l) {
        prefs(ctx).unregisterOnSharedPreferenceChangeListener(l);
    }

    public static boolean isActive(Context ctx) { return prefs(ctx).getBoolean(P_ACTIVE, false); }
    public static long endAt(Context ctx) { return prefs(ctx).getLong(P_END_AT, 0L); }
    public static int savedMode(Context ctx) { return prefs(ctx).getInt(P_SAVED_MODE, 1); }

    public static int hours(Context ctx) { return prefs(ctx).getInt(P_HOURS, 3); }
    public static void setHours(Context ctx, int h) { prefs(ctx).edit().putInt(P_HOURS, h).apply(); }

    public static boolean restoreOnUnplug(Context ctx) { return prefs(ctx).getBoolean(P_UNPLUG, false); }
    public static void setRestoreOnUnplug(Context ctx, boolean v) { prefs(ctx).edit().putBoolean(P_UNPLUG, v).apply(); }

    /** 켜면 100%에 도달해도 복구하지 않고 최대 유지 시간이 끝날 때 복구 */
    public static boolean keepAfterFull(Context ctx) { return prefs(ctx).getBoolean(P_KEEP_FULL, false); }
    public static void setKeepAfterFull(Context ctx, boolean v) { prefs(ctx).edit().putBoolean(P_KEEP_FULL, v).apply(); }

    /** 예약 실행. 요일은 Calendar.DAY_OF_WEEK 값을 비트로 묶음 (기본: 토·일 오전 6시) */
    public static boolean scheduleOn(Context ctx) { return prefs(ctx).getBoolean(P_SCHEDULE, false); }
    public static void setScheduleOn(Context ctx, boolean v) { prefs(ctx).edit().putBoolean(P_SCHEDULE, v).apply(); }

    public static int scheduleDays(Context ctx) {
        return prefs(ctx).getInt(P_SCHEDULE_DAYS, (1 << Calendar.SATURDAY) | (1 << Calendar.SUNDAY));
    }
    public static void setScheduleDays(Context ctx, int days) { prefs(ctx).edit().putInt(P_SCHEDULE_DAYS, days).apply(); }

    /** 하루 중 실행 시각(0시부터 분 단위) */
    public static int scheduleMinute(Context ctx) { return prefs(ctx).getInt(P_SCHEDULE_MINUTE, 6 * 60); }
    public static void setScheduleMinute(Context ctx, int m) { prefs(ctx).edit().putInt(P_SCHEDULE_MINUTE, m).apply(); }

    // ---------- 동작 ----------

    /** 제한 해제(100%까지 충전 허용). 서비스 시작은 호출자가 담당 */
    public static StartResult startBoost(Context ctx) {
        if (isActive(ctx)) return StartResult.ALREADY_ACTIVE;
        if (!hasPermission(ctx)) return StartResult.NO_PERMISSION;

        int mode = readMode(ctx);
        if (mode == MODE_UNSUPPORTED) return StartResult.UNSUPPORTED;
        if (mode == MODE_OFF) return StartResult.ALREADY_UNLIMITED;
        if (isLimitFull(ctx, mode)) return StartResult.LIMIT_FULL;

        long end = System.currentTimeMillis() + hours(ctx) * 3_600_000L;
        // 쓰기 전에 원래 모드를 먼저 저장(중간에 죽어도 복구 가능하도록)
        prefs(ctx).edit()
                .putInt(P_SAVED_MODE, mode)
                .putLong(P_END_AT, end)
                .putBoolean(P_ACTIVE, true)
                .commit();

        if (!write(ctx.getContentResolver(), MODE_OFF)) {
            prefs(ctx).edit().putBoolean(P_ACTIVE, false).commit();
            return StartResult.FAILED;
        }
        scheduleTimeout(ctx, end);
        ChargeWidgetProvider.updateAll(ctx);
        return StartResult.STARTED;
    }

    /** 원래 제한으로 복구. 이미 비활성이면 아무것도 안 함 */
    public static void restore(Context ctx) {
        if (!isActive(ctx)) return;
        write(ctx.getContentResolver(), savedMode(ctx));
        prefs(ctx).edit().putBoolean(P_ACTIVE, false).remove(P_END_AT).commit();
        cancelTimeout(ctx);
        ChargeWidgetProvider.updateAll(ctx);
    }

    private static boolean write(ContentResolver cr, int mode) {
        try {
            return Settings.Global.putInt(cr, KEY_MODE, mode);
        } catch (SecurityException e) {
            return false;
        }
    }

    // ---------- 시간 초과 알람 ----------

    private static PendingIntent timeoutIntent(Context ctx) {
        Intent i = new Intent(ctx, ActionReceiver.class).setAction(ActionReceiver.ACTION_RESTORE);
        return PendingIntent.getBroadcast(ctx, 1, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static void scheduleTimeout(Context ctx, long at) {
        AlarmManager am = ctx.getSystemService(AlarmManager.class);
        // 정확 알람 권한 없이 쓸 수 있는 방식. 수 분 오차 가능 → 서비스가 배터리 이벤트마다 추가로 확인함
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, timeoutIntent(ctx));
    }

    private static void cancelTimeout(Context ctx) {
        ctx.getSystemService(AlarmManager.class).cancel(timeoutIntent(ctx));
    }

    // ---------- 예약 실행 알람 ----------

    /** 다음 예약 시각. 꺼져 있거나 고른 요일이 없으면 0 */
    public static long nextScheduled(Context ctx) {
        int days = scheduleDays(ctx);
        if (!scheduleOn(ctx) || days == 0) return 0;
        int minute = scheduleMinute(ctx);
        Calendar c = Calendar.getInstance();
        long now = c.getTimeInMillis();
        c.set(Calendar.HOUR_OF_DAY, minute / 60);
        c.set(Calendar.MINUTE, minute % 60);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        // 오늘 시각이 이미 지났으면 다음 주 같은 요일까지 확인해야 하므로 8일
        for (int i = 0; i < 8; i++) {
            if ((days & (1 << c.get(Calendar.DAY_OF_WEEK))) != 0 && c.getTimeInMillis() > now) {
                return c.getTimeInMillis();
            }
            c.add(Calendar.DAY_OF_MONTH, 1);
        }
        return 0;
    }

    /** 예약 알람을 다시 맞춤. 설정 변경, 예약 실행, 재부팅·시간 변경·업데이트, 앱을 열 때 호출 */
    public static void reschedule(Context ctx) {
        AlarmManager am = ctx.getSystemService(AlarmManager.class);
        Intent i = new Intent(ctx, ActionReceiver.class).setAction(ActionReceiver.ACTION_SCHEDULE);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, 4, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        long at = nextScheduled(ctx);
        if (at == 0) {
            am.cancel(pi);
            return;
        }
        // 정확 알람(USE_EXACT_ALARM)이어야 알람을 받은 뒤 백그라운드에서 포그라운드 서비스를 시작할 수 있음
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
    }
}
