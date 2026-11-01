package io.github.nunnoo58.togglefullcharge;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.util.Date;

/** 상태 확인, 권한 안내, 설정(시간·분리 시 복구), 수동 실행 */
public class MainActivity extends Activity implements View.OnClickListener {

    private TextView status;
    private TextView adbCmd;
    private Button toggle;

    // 리스너는 약한 참조로 등록되므로 필드로 붙잡아 둠
    private final SharedPreferences.OnSharedPreferenceChangeListener stateListener =
            new SharedPreferences.OnSharedPreferenceChangeListener() {
                @Override
                public void onSharedPreferenceChanged(SharedPreferences p, String key) {
                    refresh();
                }
            };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);

        status = findViewById(R.id.status);
        adbCmd = findViewById(R.id.adb_cmd);
        adbCmd.setText("adb shell pm grant " + getPackageName()
                + " android.permission.WRITE_SECURE_SETTINGS");

        toggle = findViewById(R.id.toggle);
        findViewById(R.id.copy).setOnClickListener(this);
        toggle.setOnClickListener(this);
        findViewById(R.id.refresh).setOnClickListener(this);

        EditText hours = findViewById(R.id.hours);
        hours.setText(String.valueOf(ChargeLimiter.hours(this)));
        hours.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int c, int d) {}
            public void onTextChanged(CharSequence s, int a, int c, int d) {}
            public void afterTextChanged(Editable e) {
                try {
                    int h = Integer.parseInt(e.toString());
                    if (h >= 1 && h <= 24) ChargeLimiter.setHours(MainActivity.this, h);
                } catch (NumberFormatException ignored) {}
            }
        });

        Switch unplug = findViewById(R.id.unplug);
        unplug.setChecked(ChargeLimiter.restoreOnUnplug(this));
        unplug.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton v, boolean on) {
                ChargeLimiter.setRestoreOnUnplug(MainActivity.this, on);
            }
        });

        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.copy) {
            getSystemService(ClipboardManager.class)
                    .setPrimaryClip(ClipData.newPlainText("adb", adbCmd.getText()));
            Toast.makeText(this, "복사됨", Toast.LENGTH_SHORT).show();
        } else if (id == R.id.toggle) {
            // 위젯과 같은 토글: 해제 중이면 복구, 아니면 이번만 완전 충전
            if (ChargeLimiter.isActive(this)) {
                ChargeLimiter.restore(this);
                stopService(new Intent(this, BoostService.class));
            } else {
                startForegroundService(new Intent(this, BoostService.class)
                        .setAction(BoostService.ACTION_START));
            }
            // 화면 갱신은 상태 변경 리스너가 처리
        } else if (id == R.id.refresh) {
            refresh();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 완충·시간 초과로 서비스가 자동 복구해도 버튼이 바로 바뀌도록 상태 변경을 감시
        ChargeLimiter.listen(this, stateListener);
        refresh();
    }

    @Override
    protected void onPause() {
        ChargeLimiter.unlisten(this, stateListener);
        super.onPause();
    }

    private void refresh() {
        boolean perm = ChargeLimiter.hasPermission(this);
        int mode = ChargeLimiter.readMode(this);
        StringBuilder sb = new StringBuilder();
        sb.append("권한: ").append(perm ? "부여됨" : "없음 (아래 adb 명령 실행)").append('\n');
        sb.append("현재 배터리 보호: ").append(ChargeLimiter.describeMode(this, mode))
          .append("  [protect_battery=").append(mode)
          .append(", threshold=").append(ChargeLimiter.readThreshold(this)).append("]\n");
        boolean active = ChargeLimiter.isActive(this);
        toggle.setText(active ? "지금 제한 복구" : "이번만 완전 충전");
        if (active) {
            sb.append("상태: 100% 충전 허용 중, ")
              .append(DateFormat.getTimeInstance(DateFormat.SHORT)
                      .format(new Date(ChargeLimiter.endAt(this))))
              .append("에 자동 복구 (복구 대상 값 ")
              .append(ChargeLimiter.savedMode(this)).append(')');
        } else {
            sb.append("상태: 대기 (제한 적용 중)");
        }
        status.setText(sb);
        findViewById(R.id.perm_box).setVisibility(perm ? View.GONE : View.VISIBLE);
        ChargeWidgetProvider.updateAll(this);
    }
}
