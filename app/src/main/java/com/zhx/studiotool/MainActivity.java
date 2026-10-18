// zhxstudio — surfgobbypass
package com.zhx.studiotool;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.*;
import rikka.shizuku.Shizuku;

public final class MainActivity extends Activity {
    private SharedPreferences prefs;
    private boolean waitingOverlay;
    private final Shizuku.OnRequestPermissionResultListener listener = (code, result) -> {
        if (code == 100) runOnUiThread(() -> {
            if (result == PackageManager.PERMISSION_GRANTED) startOverlay();
            else toast(getString(R.string.denied_shizuku));
        });
    };
    @Override protected void attachBaseContext(Context base) { super.attachBaseContext(AppLanguage.wrap(base)); }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        prefs = getSharedPreferences("settings", MODE_PRIVATE);
        waitingOverlay = state != null && state.getBoolean("waitingOverlay");
        View root = findViewById(R.id.root);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        root.requestApplyInsets();
        Shizuku.addRequestPermissionResultListener(listener);
        Spinner languages = findViewById(R.id.languages);
        String[] codes = {"ru", "en", "sr"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item,
                new String[]{"Русский", "English", "Српски"});
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        languages.setAdapter(adapter);
        String selected = prefs.getString("language", "ru");
        languages.setSelection("en".equals(selected) ? 1 : "sr".equals(selected) ? 2 : 0);
        languages.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!codes[position].equals(prefs.getString("language", "ru"))) {
                    prefs.edit().putString("language", codes[position]).apply();
                    recreate();
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        RadioGroup methods = findViewById(R.id.methods);
        methods.check("root".equals(prefs.getString("method", "shizuku")) ? R.id.root_method : R.id.shizuku_method);
        methods.setOnCheckedChangeListener((group, id) -> prefs.edit().putString("method",
                id == R.id.root_method ? "root" : "shizuku").apply());
        if (!prefs.contains("method")) prefs.edit().putString("method", "shizuku").apply();
        SeekBar size = findViewById(R.id.size);
        TextView label = findViewById(R.id.size_label);
        int value = prefs.getInt("size", 64);
        size.setProgress(value - 48);
        label.setText(getString(R.string.size, value));
        size.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean user) {
                label.setText(getString(R.string.size, progress + 48));
                prefs.edit().putInt("size", progress + 48).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
        findViewById(R.id.start).setOnClickListener(v -> startOverlay());
        findViewById(R.id.stop).setOnClickListener(v -> stopService(new Intent(this, OverlayService.class)));
        findViewById(R.id.diagnostics).setOnClickListener(v -> {
            String error = prefs.getString("last_error", "");
            String detail = "Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")\n"
                    + "Target: " + ShellRunner.TARGET_PACKAGE + "\n"
                    + (error.isEmpty() ? getString(R.string.no_error) : error);
            new AlertDialog.Builder(this).setTitle(R.string.diagnostics).setMessage(detail)
                    .setPositiveButton(android.R.string.ok, null)
                    .setNeutralButton(R.string.copy, (dialog, which) -> getSystemService(ClipboardManager.class)
                            .setPrimaryClip(ClipData.newPlainText("surfgobbypass", detail))).show();
        });
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
        }
    }
    private void startOverlay() {
        try {
            if (!Settings.canDrawOverlays(this)) {
                waitingOverlay = true;
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
                return;
            }
            if ("shizuku".equals(prefs.getString("method", "shizuku"))) {
                if (!Shizuku.pingBinder() || Shizuku.getVersion() < 12) {
                    toast(getString(R.string.require_shizuku)); return;
                }
                if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                    if (Shizuku.shouldShowRequestPermissionRationale()) toast(getString(R.string.grant_shizuku));
                    else Shizuku.requestPermission(100);
                    return;
                }
            }
            startForegroundService(new Intent(this, OverlayService.class));
        } catch (RuntimeException e) { toast(getString(R.string.error) + ": " + e.getMessage()); }
    }
    @Override protected void onResume() {
        super.onResume();
        if (waitingOverlay) {
            waitingOverlay = false;
            if (Settings.canDrawOverlays(this)) startOverlay();
            else toast(getString(R.string.overlay_denied));
        }
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("waitingOverlay", waitingOverlay);
        super.onSaveInstanceState(state);
    }
    @Override protected void onDestroy() {
        Shizuku.removeRequestPermissionResultListener(listener);
        super.onDestroy();
    }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
}
