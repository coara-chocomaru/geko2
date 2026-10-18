package com.coara.browser2;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.coara.browser2.util.BrowserConstants;
import com.coara.browser2.util.CookiePolicyUi;
import com.coara.browser2.util.CookieSitePolicy;
import com.coara.browser2.util.DeviceInfoProvider;
import com.coara.browser2.util.TextFileReader;
import com.coara.browser2.util.GeckoEngineInfo;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import io.noties.markwon.Markwon;

public class SettingsActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private SettingsAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        List<SettingItem> settingsList = new ArrayList<>();
        settingsList.add(new SettingItem("アプリ情報", "アプリの情報", this::openAppInfo));
        settingsList.add(new SettingItem("端末情報", "デバイスの基本情報を表示", this::showDeviceInfo));
        settingsList.add(new SettingItem("Cookieのブロックと許可", cookieSummary(), this::showCookieDialog));
        settingsList.add(new SettingItem("Gecko エンジン情報", "現在のエンジンバージョンと対応機能", this::showWebViewEngine));
        settingsList.add(new SettingItem("アプリバージョン", getAppVersion(), null));
        settingsList.add(new SettingItem("ライセンス", "ライセンス情報を表示", this::showLicense));

        adapter = new SettingsAdapter(settingsList);
        recyclerView.setAdapter(adapter);
    }

    private SharedPreferences browserPrefs() {
        return getSharedPreferences(BrowserConstants.PREF_NAME, Context.MODE_PRIVATE);
    }

    private String cookieSummary() {
        SharedPreferences prefs = browserPrefs();
        int mode = CookieSitePolicy.mode(prefs);
        int allowed = CookieSitePolicy.sites(prefs, true).size();
        int blocked = CookieSitePolicy.sites(prefs, false).size();
        String base;
        if (mode == CookieSitePolicy.MODE_BLOCK_ALL) {
            base = "クッキーを常にブロック&拒否中(すべてのCookieを保存・送信しません)";
        } else if (mode == CookieSitePolicy.MODE_ALLOW_THIRD_PARTY) {
            base = "サードパーティCookieを許可中(互換性優先・追跡されやすくなります)";
        } else {
            base = "サードパーティCookieをブロック中(推奨)";
        }
        if (mode != CookieSitePolicy.MODE_BLOCK_ALL && (allowed > 0 || blocked > 0)) {
            base += "\nサイト別: 許可 " + allowed + " / ブロック " + blocked;
        }
        return base;
    }

    private void showCookieDialog() {
        CookiePolicyUi.showGlobalDialog(this, browserPrefs(), this::recreate);
    }

    private String getAppVersion() {
        return DeviceInfoProvider.getAppVersion(this);
    }

    private void openAppInfo() {
        startActivity(DeviceInfoProvider.createAppInfoIntent(this));
    }

    private void showDeviceInfo() {
        showTextDialog("端末情報", DeviceInfoProvider.collectDeviceInfo());
    }

    private void showWebViewEngine() {
        showTextDialog("Gecko エンジン情報", GeckoEngineInfo.collect(this));
    }

    private void showLicense() {
        showMarkdownDialog("ライセンス情報", readAssetText("LICENSE.MD"));
    }

    private String readAssetText(String assetName) {
        try (InputStream inputStream = getAssets().open(assetName)) {
            return TextFileReader.readAll(inputStream);
        } catch (Exception e) {
            return "ライセンス情報を取得できません";
        }
    }

    private void showTextDialog(String title, CharSequence message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);
        builder.setMessage(message);
        builder.setPositiveButton("閉じる", null);
        builder.show();
    }

    private void showMarkdownDialog(String title, String markdown) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);

        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_markdown, null);
        TextView textView = dialogView.findViewById(R.id.markdownText);

        Markwon markwon = Markwon.create(this);
        markwon.setMarkdown(textView, markdown);

        builder.setView(dialogView);
        builder.setPositiveButton("閉じる", null);
        builder.show();
    }
}
