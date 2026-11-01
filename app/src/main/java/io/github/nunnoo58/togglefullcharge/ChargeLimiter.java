package io.github.nunnoo58.togglefullcharge;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.provider.Settings;

/**
 * 삼성 One UI '배터리 보호' 설정(Settings.Global)을 읽고 쓰는 핵심 로직.
 *
 * protect_battery 값 (One UI 6.1 ~ 9 기준)
 *   0 = 꺼짐, 1 = 최대(battery_protection_threshold %에서 정지), 3 = 기본, 4 = 수면 시간 보호
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

    public enum StartResult { STARTED, NO_PERMISSION, UNSUPPORTED, ALREADY_UNLIMITED, ALREADY_ACTIVE, FAILED }

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
            case MODE_UNSUPPORTED: return "지원 안 됨(설정 키 없음)";
            case 0: return "꺼짐";
            case 1:
                int t = readThreshold(ctx);
                return "최대 (" + (t > 0 ? t : 80) + "%에서 정지)";
            case 3: return "기본";
            case 4: return "수면 시간 보호";
            default: return "알 수 없는 값(" + mode + ")";
        }
    }

    /** 위젯에 표시할 제한 퍼센트. 최대 모드가 아니면 -1 */
    public static int limitPercent(Context ctx, int mode) {
        if (mode != 1) return -1;
        int t = readThreshold(ctx);
        return t > 0 ? t : 80;
    }

    // ---------- 상태 저장 ----------

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
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

    // ---------- 동작 ----------

    /** 제한 해제(100%까지 충전 허용). 서비스 시작은 호출자가 담당 */
    public static StartResult startBoost(Context ctx) {
        if (isActive(ctx)) return StartResult.ALREADY_ACTIVE;
        if (!hasPermission(ctx)) return StartResult.NO_PERMISSION;

        int mode = readMode(ctx);
        if (mode == MODE_UNSUPPORTED) return StartResult.UNSUPPORTED;
        if (mode == MODE_OFF) return StartResult.ALREADY_UNLIMITED;

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
}
