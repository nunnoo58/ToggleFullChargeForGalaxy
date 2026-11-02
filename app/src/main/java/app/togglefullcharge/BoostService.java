package app.togglefullcharge;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.os.BatteryManager;
import android.os.IBinder;
import android.widget.Toast;

import java.text.DateFormat;
import java.util.Date;

/**
 * 제한 해제 상태 동안만 실행되는 포그라운드 서비스.
 * 배터리 상태를 감시하다가 완충 / 시간 초과 / (옵션) 분리 시 원래 제한으로 복구한다.
 */
public class BoostService extends Service {

    public static final String ACTION_TOGGLE = "app.togglefullcharge.TOGGLE";
    public static final String ACTION_START = "app.togglefullcharge.START";

    private static final String CHANNEL = "boost";
    private static final int NOTI_ID = 1;
    /** 제한 해제 후 이 시간이 지나도 충전이 재개되지 않으면 케이블 재연결 안내 */
    private static final long STALL_HINT_MS = 90_000L;

    private BroadcastReceiver batteryReceiver;
    private boolean wasPlugged = false;
    private long startedAt;
    private boolean stallHintShown = false;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // getForegroundService로 시작되면 5초 안에 startForeground를 반드시 호출해야 함
        goForeground("확인 중…");

        String action = intent != null ? intent.getAction() : null;
        if (ACTION_TOGGLE.equals(action) && ChargeLimiter.isActive(this)) {
            ChargeLimiter.restore(this);
            toast("충전 제한 복구");
            stopSelfNow();
            return START_NOT_STICKY;
        }

        if (!ChargeLimiter.isActive(this)) {
            ChargeLimiter.StartResult r = ChargeLimiter.startBoost(this);
            if (r != ChargeLimiter.StartResult.STARTED) {
                toast(messageFor(r));
                if (r == ChargeLimiter.StartResult.NO_PERMISSION
                        || r == ChargeLimiter.StartResult.UNSUPPORTED) {
                    startActivity(new Intent(this, MainActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                }
                stopSelfNow();
                return START_NOT_STICKY;
            }
            toast("이번만 100%까지 충전");
        }

        startedAt = System.currentTimeMillis();
        stallHintShown = false;
        registerBatteryWatcher();
        updateNotification(null);
        return START_NOT_STICKY;
    }

    private void registerBatteryWatcher() {
        if (batteryReceiver != null) return;
        batteryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) { onBattery(i); }
        };
        // ACTION_BATTERY_CHANGED는 sticky라 등록 즉시 현재 상태가 한 번 들어옴
        Intent sticky = registerReceiver(batteryReceiver,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (sticky != null) {
            wasPlugged = sticky.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
        }
    }

    private void onBattery(Intent i) {
        if (!ChargeLimiter.isActive(this)) { stopSelfNow(); return; }

        int level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        int pct = level >= 0 && scale > 0 ? Math.round(level * 100f / scale) : -1;
        int status = i.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN);
        boolean plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;

        // 1) 완충 감지
        if (pct >= 100 || status == BatteryManager.BATTERY_STATUS_FULL) {
            finishWith("완충 감지 → 충전 제한 복구");
            return;
        }
        // 2) 시간 초과 (알람이 늦을 때의 보조 확인)
        if (System.currentTimeMillis() >= ChargeLimiter.endAt(this)) {
            finishWith("시간 초과 → 충전 제한 복구");
            return;
        }
        // 3) 분리 시 복구 (옵션)
        if (ChargeLimiter.restoreOnUnplug(this) && wasPlugged && !plugged) {
            finishWith("충전기 분리 → 충전 제한 복구");
            return;
        }
        wasPlugged = plugged;

        // 제한을 풀었는데도 '충전 안 함' 상태로 멈춰 있으면 안내
        String hint = null;
        if (plugged && status == BatteryManager.BATTERY_STATUS_NOT_CHARGING
                && System.currentTimeMillis() - startedAt > STALL_HINT_MS) {
            hint = "충전이 재개되지 않으면 케이블을 뺐다가 다시 꽂을 것";
            stallHintShown = true;
        } else if (stallHintShown && status == BatteryManager.BATTERY_STATUS_CHARGING) {
            stallHintShown = false;
        }
        updateNotification(pct >= 0 ? pct + "%" + (hint != null ? " · " + hint : "") : hint);
    }

    private void finishWith(String msg) {
        ChargeLimiter.restore(this);
        toast(msg);
        stopSelfNow();
    }

    // ---------- 알림 ----------

    private void goForeground(String text) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(new NotificationChannel(
                    CHANNEL, "완전 충전 진행", NotificationManager.IMPORTANCE_LOW));
        }
        startForeground(NOTI_ID, build(text), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
    }

    private void updateNotification(String extra) {
        String until = DateFormat.getTimeInstance(DateFormat.SHORT)
                .format(new Date(ChargeLimiter.endAt(this)));
        String text = "완충 또는 " + until + "에 자동 복구" + (extra != null ? " · " + extra : "");
        getSystemService(NotificationManager.class).notify(NOTI_ID, build(text));
    }

    private Notification build(String text) {
        Intent restore = new Intent(this, ActionReceiver.class).setAction(ActionReceiver.ACTION_RESTORE);
        PendingIntent restorePi = PendingIntent.getBroadcast(this, 2, restore,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent openPi = PendingIntent.getActivity(this, 3,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);

        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_bolt)
                .setContentTitle("이번만 100%까지 충전 중")
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setOngoing(true)
                .setContentIntent(openPi)
                .addAction(new Notification.Action.Builder(null, "지금 제한 복구", restorePi).build())
                .build();
    }

    // ---------- 기타 ----------

    private void stopSelfNow() {
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    static String messageFor(ChargeLimiter.StartResult r) {
        switch (r) {
            case NO_PERMISSION: return "권한 없음: 앱을 열어 adb 권한 부여 필요";
            case UNSUPPORTED: return "이 기기는 배터리 보호 설정 키가 없음";
            case ALREADY_UNLIMITED: return "배터리 보호가 이미 꺼져 있음";
            case ALREADY_ACTIVE: return "이미 완전 충전 진행 중";
            case FAILED: return "설정 변경 실패";
            default: return "";
        }
    }

    @Override
    public void onDestroy() {
        if (batteryReceiver != null) {
            unregisterReceiver(batteryReceiver);
            batteryReceiver = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
