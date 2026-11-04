package app.togglefullcharge;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.util.Linkify;
import android.view.View;
import android.widget.TextView;

/** 햄버거 메뉴에서 여는 만든 사람·법적 고지 페이지 */
public class InfoActivity extends Activity {

    public static final String EXTRA_PAGE = "page";
    public static final int PAGE_ABOUT = 0;
    public static final int PAGE_LEGAL = 1;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_info);

        findViewById(R.id.back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { finish(); }
        });

        TextView title = findViewById(R.id.title);
        TextView body = findViewById(R.id.body);
        if (getIntent().getIntExtra(EXTRA_PAGE, PAGE_ABOUT) == PAGE_LEGAL) {
            title.setText(R.string.menu_legal);
            body.setText(R.string.legal_text);
        } else {
            title.setText(R.string.menu_about);
            // GitHub 주소를 눌러 열 수 있게 함
            body.setAutoLinkMask(Linkify.WEB_URLS);
            body.setText(getString(R.string.about_text, versionName()));
        }
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "?";
        }
    }
}
