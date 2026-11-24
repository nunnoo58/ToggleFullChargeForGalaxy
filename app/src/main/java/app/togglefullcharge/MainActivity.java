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
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.PopupMenu;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import java.text.DateFormat;
import java.text.DateFormatSymbols;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** 상태 확인, 권한 안내, 설정(시간·분리 시 복구·예약 실행), 수동 실행 */
public class MainActivity extends Activity implements View.OnClickListener {

    // 메뉴 항목 id (InfoActivity.PAGE_* 와 겹치지 않게)
    private static final int MENU_LANGUAGES = 100;
    /** 요일 선택 창과 요약에 쓰는 순서 (월요일부터) */
    private static final int[] DAY_ORDER = {Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
            Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY};
    private static final int DAYS_ALL = (1 << Calendar.MONDAY) | (1 << Calendar.TUESDAY) | (1 << Calendar.WEDNESDAY)
            | (1 << Calendar.THURSDAY) | (1 << Calendar.FRIDAY) | (1 << Calendar.SATURDAY) | (1 << Calendar.SUNDAY);
    private static final int DAYS_WEEKEND = (1 << Calendar.SATURDAY) | (1 << Calendar.SUNDAY);
    private static final int DAYS_WEEKDAYS = DAYS_ALL & ~DAYS_WEEKEND;

    private TextView status;
    private TextView adbCmd;
    private Button toggle;
    private EditText hours;
    private Button scheduleDays;
    private Button scheduleTime;

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

        Switch keepFull = findViewById(R.id.keep_full);
        keepFull.setChecked(ChargeLimiter.keepAfterFull(this));
        keepFull.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton v, boolean on) {
                ChargeLimiter.setKeepAfterFull(MainActivity.this, on);
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

        Switch schedule = findViewById(R.id.schedule);
        schedule.setChecked(ChargeLimiter.scheduleOn(this));
        schedule.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton v, boolean on) {
                ChargeLimiter.setScheduleOn(MainActivity.this, on);
                ChargeLimiter.reschedule(MainActivity.this);
            }
        });
        scheduleDays = findViewById(R.id.schedule_days);
        scheduleTime = findViewById(R.id.schedule_time);
        scheduleDays.setOnClickListener(this);
        scheduleTime.setOnClickListener(this);

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
        } else if (id == R.id.schedule_days) {
            pickDays();
        } else if (id == R.id.schedule_time) {
            pickTime();
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
        // 강제 종료하면 알람이 지워지므로 앱을 열 때마다 예약을 다시 맞춤
        ChargeLimiter.reschedule(this);
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

    /** 요일 7개를 가로 한 줄 동그라미 토글로 만듦. 누를 때마다 days[0]을 바꿈 */
    private void buildDayRow(LinearLayout row, final int[] days) {
        String[] shortNames = new DateFormatSymbols().getShortWeekdays();
        int size = Math.round(40 * getResources().getDisplayMetrics().density);
        for (int i = 0; i < DAY_ORDER.length; i++) {
            final int bit = 1 << DAY_ORDER[i];
            final TextView t = new TextView(this);
            t.setText(shortNames[DAY_ORDER[i]]);
            t.setGravity(Gravity.CENTER);
            t.setBackgroundResource(R.drawable.day_bg);
            t.setTextColor(getColorStateList(R.color.day_text));
            t.setSelected((days[0] & bit) != 0);
            t.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    days[0] ^= bit;
                    t.setSelected((days[0] & bit) != 0);
                }
            });
            FrameLayout cell = new FrameLayout(this);
            cell.addView(t, new FrameLayout.LayoutParams(size, size, Gravity.CENTER));
            row.addView(cell, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        }
    }

    /** 요일 선택 창. 요일 7개를 가로 한 줄 토글로 보여 주고 확인을 누르면 저장 */
    private void pickDays() {
        final int[] days = {ChargeLimiter.scheduleDays(this)};
        LinearLayout row = new LinearLayout(this);
        int pad = Math.round(16 * getResources().getDisplayMetrics().density);
        row.setPadding(pad, pad, pad, 0);
        buildDayRow(row, days);
        new AlertDialog.Builder(this)
                .setTitle(R.string.schedule_days_title)
                .setView(row)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        ChargeLimiter.setScheduleDays(MainActivity.this, days[0]);
                        ChargeLimiter.reschedule(MainActivity.this);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void pickTime() {
        int m = ChargeLimiter.scheduleMinute(this);
        final TimePicker tp = (TimePicker) getLayoutInflater().inflate(R.layout.time_picker, null);
        tp.setIs24HourView(android.text.format.DateFormat.is24HourFormat(this));
        tp.setHour(m / 60);
        tp.setMinute(m % 60);
        enlargeWheel(tp);
        new AlertDialog.Builder(this)
                .setView(tp)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        // 키보드로 입력 중인 숫자는 포커스를 잃어야 반영됨
                        tp.clearFocus();
                        ChargeLimiter.setScheduleMinute(MainActivity.this, tp.getHour() * 60 + tp.getMinute());
                        ChargeLimiter.reschedule(MainActivity.this);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** 시각 휠의 글자를 20% 키움. 시스템 휠이라 기본 크기가 정해져 있어 직접 바꿈 */
    private static void enlargeWheel(ViewGroup g) {
        for (int i = 0; i < g.getChildCount(); i++) {
            View c = g.getChildAt(i);
            if (c instanceof NumberPicker) {
                NumberPicker np = (NumberPicker) c;
                np.setTextSize(np.getTextSize() * 1.2f);
            } else if (c instanceof TextView) {  // 시와 분 사이의 ":"
                TextView t = (TextView) c;
                t.setTextSize(TypedValue.COMPLEX_UNIT_PX, t.getTextSize() * 1.2f);
            } else if (c instanceof ViewGroup) {
                enlargeWheel((ViewGroup) c);
            }
        }
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
            sb.append(getString(ChargeLimiter.isLimitFull(this, mode)
                    ? R.string.status_limit_full : R.string.status_idle));
        }
        long next = ChargeLimiter.nextScheduled(this);
        if (next > 0) {
            String pattern = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "MMMdEEEjmm");
            sb.append('\n').append(getString(R.string.status_next,
                    android.text.format.DateFormat.format(pattern, next)));
        }
        status.setText(sb);

        // 예약 요일·시각 버튼 (설정이 바뀌면 상태 변경 리스너가 이 함수를 다시 부름)
        int days = ChargeLimiter.scheduleDays(this);
        String[] shortNames = new DateFormatSymbols().getShortWeekdays();
        StringBuilder dayText = new StringBuilder();
        for (int d : DAY_ORDER) {
            if ((days & (1 << d)) == 0) continue;
            if (dayText.length() > 0) dayText.append(", ");
            dayText.append(shortNames[d]);
        }
        // 정확히 월~금, 토·일, 7일 전부일 때는 묶음 이름으로
        if (days == 0) {
            scheduleDays.setText(R.string.schedule_days_none);
        } else if (days == DAYS_ALL) {
            scheduleDays.setText(R.string.schedule_every_day);
        } else if (days == DAYS_WEEKDAYS) {
            scheduleDays.setText(R.string.schedule_weekdays);
        } else if (days == DAYS_WEEKEND) {
            scheduleDays.setText(R.string.schedule_weekends);
        } else {
            scheduleDays.setText(dayText);
        }
        findViewById(R.id.schedule_row).setVisibility(ChargeLimiter.scheduleOn(this) ? View.VISIBLE : View.GONE);
        Calendar c = Calendar.getInstance();
        int m = ChargeLimiter.scheduleMinute(this);
        c.set(Calendar.HOUR_OF_DAY, m / 60);
        c.set(Calendar.MINUTE, m % 60);
        scheduleTime.setText(DateFormat.getTimeInstance(DateFormat.SHORT).format(c.getTime()));
        findViewById(R.id.perm_box).setVisibility(perm ? View.GONE : View.VISIBLE);
        ChargeWidgetProvider.updateAll(this);
    }
}
