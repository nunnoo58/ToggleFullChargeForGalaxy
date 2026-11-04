package app.togglefullcharge;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.UserManager;
import android.widget.RemoteViews;

import java.text.DateFormat;
import java.util.Date;

/** 1x1 위젯. 누르면 제한 해제 ↔ 복구 토글 */
public class ChargeWidgetProvider extends AppWidgetProvider {

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        render(ctx, mgr, ids);
    }

    public static void updateAll(Context ctx) {
        // 재부팅 후 잠금 해제 전에는 위젯 서비스를 쓸 수 없음. 해제 후 BOOT_COMPLETED에서 다시 갱신됨
        if (!ctx.getSystemService(UserManager.class).isUserUnlocked()) return;
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, ChargeWidgetProvider.class));
        if (ids.length > 0) render(ctx, mgr, ids);
    }

    private static void render(Context ctx, AppWidgetManager mgr, int[] ids) {
        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget);
        boolean active = ChargeLimiter.isActive(ctx);
        boolean ok = ChargeLimiter.hasPermission(ctx);

        if (!ok) {
            v.setTextViewText(R.id.big, ctx.getString(R.string.widget_need_perm_big));
            v.setTextViewText(R.id.small, ctx.getString(R.string.widget_need_perm));
            v.setImageViewResource(R.id.bg, R.drawable.widget_bg_idle);
        } else if (active) {
            String until = DateFormat.getTimeInstance(DateFormat.SHORT)
                    .format(new Date(ChargeLimiter.endAt(ctx)));
            v.setTextViewText(R.id.big, "100%");
            v.setTextViewText(R.id.small, "~" + until);
            v.setImageViewResource(R.id.bg, R.drawable.widget_bg_active);
        } else {
            int mode = ChargeLimiter.readMode(ctx);
            int limit = ChargeLimiter.limitPercent(ctx, mode);
            v.setTextViewText(R.id.big, limit > 0 ? limit + "%"
                    : ctx.getString(mode == 0 ? R.string.widget_off : R.string.widget_protect));
            // 최대 모드 한도가 100%면 눌러도 소용없으므로 한도를 낮추라고 안내
            v.setTextViewText(R.id.small, ctx.getString(ChargeLimiter.isLimitFull(ctx, mode)
                    ? R.string.widget_lower_limit : R.string.widget_tap));
            v.setImageViewResource(R.id.bg, R.drawable.widget_bg_idle);
        }

        PendingIntent click;
        if (ok) {
            Intent i = new Intent(ctx, BoostService.class).setAction(BoostService.ACTION_TOGGLE);
            // 위젯 클릭에서 시작하는 포그라운드 서비스는 백그라운드 시작 제한의 예외에 해당
            click = PendingIntent.getForegroundService(ctx, 0, i,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        } else {
            click = PendingIntent.getActivity(ctx, 0, new Intent(ctx, MainActivity.class),
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        }
        v.setOnClickPendingIntent(R.id.root, click);
        mgr.updateAppWidget(ids, v);
    }
}
