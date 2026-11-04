package app.togglefullcharge;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.LocaleManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.LocaleList;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.util.Date;

/** 상태 확인, 권한 안내, 설정(시간·분리 시 복구), 수동 실행 */
public class MainActivity extends Activity implements View.OnClickListener {

    // 메뉴 항목 id (InfoActivity.PAGE_* 와 겹치지 않게)
    private static final int MENU_LANGUAGES = 100;

    private TextView status;
    private TextView adbCmd;
    private Button toggle;
    private EditText hours;

    // 리스너는 약한 참조로 등록되므로 필드로 붙잡아 둠
    private final SharedPreferences.OnSharedPreferenceChangeListener stateListener =
            new SharedPreferences.OnSharedPreferenceChangeListener() {
                @Override
                public void onSharedPreferenceChanged(SharedPreferences p, String key) {
                    refresh();
                }
            };

    // 배터리 보호 설정 값이 바뀌어도 바로 갱신 (완충 시작은 상태 저장 뒤에 설정을 바꾸고, 설정 앱에서 직접 바꿀 수도 있음)
    private final ContentObserver settingsObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
        @Override
        public void onChange(boolean selfChange) {
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
        findViewById(R.id.menu).setOnClickListener(this);
        findViewById(R.id.copy).setOnClickListener(this);
        toggle.setOnClickListener(this);
        findViewById(R.id.refresh).setOnClickListener(this);

        hours = findViewById(R.id.hours);
        hours.setText(String.valueOf(ChargeLimiter.hours(this)));
        // 글자마다 저장하면 "25"를 칠 때 "2"가 먼저 저장되므로, 입력을 마칠 때 한 번만 반영
        hours.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent e) {
                commitHours();
                return false;  // 기본 동작(키보드 닫기)은 그대로
            }
        });
        hours.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) commitHours();
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

        // 거부해도 화면이 새로 만들어질 때마다 다시 묻지 않도록 처음 한 번만 요청
        SharedPreferences ui = getPreferences(MODE_PRIVATE);
        if (!ui.getBoolean("notification_asked", false)
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            ui.edit().putBoolean("notification_asked", true).apply();
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.menu) {
            PopupMenu menu = new PopupMenu(this, v);
            menu.getMenu().add(0, MENU_LANGUAGES, 0, R.string.menu_languages);
            menu.getMenu().add(0, InfoActivity.PAGE_ABOUT, 1, R.string.menu_about);
            menu.getMenu().add(0, InfoActivity.PAGE_LEGAL, 2, R.string.menu_legal);
            menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    if (item.getItemId() == MENU_LANGUAGES) {
                        showLanguages();
                    } else {
                        startActivity(new Intent(MainActivity.this, InfoActivity.class)
                                .putExtra(InfoActivity.EXTRA_PAGE, item.getItemId()));
                    }
                    return true;
                }
            });
            menu.show();
        } else if (id == R.id.copy) {
            getSystemService(ClipboardManager.class)
                    .setPrimaryClip(ClipData.newPlainText("adb", adbCmd.getText()));
            Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show();
        } else if (id == R.id.toggle) {
            // 입력란에 포커스가 남은 채 누르면 포커스 이동이 없으므로 여기서 반영
            commitHours();
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
        getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(ChargeLimiter.KEY_MODE), false, settingsObserver);
        getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(ChargeLimiter.KEY_THRESHOLD), false, settingsObserver);
        refresh();
    }

    @Override
    protected void onPause() {
        commitHours();
        ChargeLimiter.unlisten(this, stateListener);
        getContentResolver().unregisterContentObserver(settingsObserver);
        super.onPause();
    }

    /** 앱 언어 선택. Android 13+의 앱별 언어를 쓰므로 고른 언어는 시스템이 저장하고 화면을 다시 만듦 */
    private void showLanguages() {
        final String[] tags = {"ko", "en", "ja"};
        String[] names = {getString(R.string.lang_ko), getString(R.string.lang_en), getString(R.string.lang_ja)};
        new AlertDialog.Builder(this)
                .setTitle(R.string.languages_title)
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        getSystemService(LocaleManager.class)
                                .setApplicationLocales(LocaleList.forLanguageTags(tags[which]));
                    }
                })
                .show();
    }

    /** 입력값이 1~24면 저장, 아니면 저장된 값으로 되돌림 (화면 값과 실제 값을 항상 일치시킴) */
    private void commitHours() {
        try {
            int h = Integer.parseInt(hours.getText().toString());
            if (h >= 1 && h <= 24) {
                ChargeLimiter.setHours(this, h);
                return;
            }
        } catch (NumberFormatException ignored) {}
        String saved = String.valueOf(ChargeLimiter.hours(this));
        if (!saved.contentEquals(hours.getText())) {
            hours.setText(saved);
            Toast.makeText(this, R.string.hours_invalid, Toast.LENGTH_SHORT).show();
        }
    }

    private void refresh() {
        boolean perm = ChargeLimiter.hasPermission(this);
        int mode = ChargeLimiter.readMode(this);
        StringBuilder sb = new StringBuilder();
        sb.append(getString(R.string.status_perm,
                getString(perm ? R.string.perm_granted : R.string.perm_missing))).append('\n');
        sb.append(getString(R.string.status_mode, ChargeLimiter.describeMode(this, mode))).append('\n');
        boolean active = ChargeLimiter.isActive(this);
        toggle.setText(active ? R.string.btn_restore : R.string.btn_boost);
        if (active) {
            sb.append(getString(R.string.status_active, DateFormat.getTimeInstance(DateFormat.SHORT)
                    .format(new Date(ChargeLimiter.endAt(this)))));
        } else {
            sb.append(getString(R.string.status_idle));
        }
        status.setText(sb);
        findViewById(R.id.perm_box).setVisibility(perm ? View.GONE : View.VISIBLE);
        ChargeWidgetProvider.updateAll(this);
    }
}
