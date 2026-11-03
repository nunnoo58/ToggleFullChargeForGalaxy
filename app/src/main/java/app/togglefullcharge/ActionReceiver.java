package app.togglefullcharge;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 시간 초과 알람, 알림의 '지금 제한 복구' 버튼, 재부팅을 처리 */
public class ActionReceiver extends BroadcastReceiver {

    public static final String ACTION_RESTORE = "app.togglefullcharge.RESTORE";

    @Override
    public void onReceive(Context ctx, Intent intent) {
        String action = intent.getAction();
        if (ACTION_RESTORE.equals(action)
                || Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            // 재부팅 중에 해제 상태가 남아 있었다면 안전하게 원래 제한으로 되돌림
            // (LOCKED_BOOT_COMPLETED는 잠금 해제 전, BOOT_COMPLETED는 해제 후. 이미 복구됐으면 아무것도 안 함)
            ChargeLimiter.restore(ctx);
            ctx.stopService(new Intent(ctx, BoostService.class));
            ChargeWidgetProvider.updateAll(ctx);
        }
    }
}
