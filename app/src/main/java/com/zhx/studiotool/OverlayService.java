// zhxstudio — surfgobbypass
package com.zhx.studiotool;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import rikka.shizuku.Shizuku;

public final class OverlayService extends Service
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String STOP = "com.zhx.studiotool.STOP";
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private WindowManager windows;
    private WindowManager.LayoutParams params;
    private SharedPreferences prefs;
    private Button button;
    private boolean destroyed, busy, bound, pending;
    private IShellService remote;
    private Shizuku.UserServiceArgs userArgs;
    private float downX, downY;
    private int initialX, initialY;
    private boolean dragging;

    private final Runnable bindTimeout = () -> {
        boolean waiting = pending;
        pending = false;
        disconnect();
        if (waiting) finish(t(R.string.bind_timeout));
    };

    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            main.post(() -> {
                if (destroyed || !bound) return;
                remote = IShellService.Stub.asInterface(binder);
                main.removeCallbacks(bindTimeout);
                if (pending) {
                    pending = false;
                    execute("shizuku");
                }
            });
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            main.post(() -> {
                disconnect();
                if (pending) {
                    pending = false;
                    main.removeCallbacks(bindTimeout);
                    finish(t(R.string.disconnected));
                }
            });
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("settings", MODE_PRIVATE);
        windows = getSystemService(WindowManager.class);
        userArgs = new Shizuku.UserServiceArgs(new ComponentName(this, ShellService.class))
                .daemon(false).processNameSuffix("shell").version(7);
        updateNotification();
        prefs.registerOnSharedPreferenceChangeListener(this);
    }

    private String t(int id) { return AppLanguage.text(this, id); }

    private void updateNotification() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("overlay", t(R.string.channel),
                NotificationManager.IMPORTANCE_LOW));
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 1,
                new Intent(this, OverlayService.class).setAction(STOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(this, "overlay")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentTitle(t(R.string.app_name)).setContentText(t(R.string.notification))
                .setContentIntent(open).setOngoing(true)
                .addAction(new Notification.Action.Builder(null, t(R.string.stop), stop).build()).build();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else startForeground(1, notification);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int id) {
        if ((intent != null && STOP.equals(intent.getAction())) || !Settings.canDrawOverlays(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (button == null) {
            try { createButton(); }
            catch (RuntimeException e) { toast(t(R.string.error) + ": " + e.getMessage()); stopSelf(); }
        }
        warmShizuku();
        warmRoot();
        return START_NOT_STICKY;
    }

    private void warmShizuku() {
        if (destroyed || bound || !"shizuku".equals(prefs.getString("method", "root"))) return;
        try {
            if (Shizuku.pingBinder() && Shizuku.getVersion() >= 12
                    && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                bindShizuku();
            }
        } catch (RuntimeException e) {
            disconnect();
            Log.w("ZHX", "Shizuku preconnect failed", e);
        }
    }

    private void warmRoot() {
        if (!destroyed && "root".equals(prefs.getString("method", "shizuku"))) {
            executor.execute(() -> { if (!destroyed) ShellRunner.prepare(true); });
        }
    }

    private void bindShizuku() {
        bound = true;
        main.removeCallbacks(bindTimeout);
        main.postDelayed(bindTimeout, 15000);
        Shizuku.bindUserService(userArgs, connection);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void createButton() {
        button = new Button(this);
        button.setText("B");
        button.setTextSize(22);
        button.setTextColor(Color.WHITE);
        button.setGravity(Gravity.CENTER);
        button.setPadding(0, 0, 0, 0);
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setContentDescription("Bypass Surf GO");
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        background.setColor(Color.rgb(21, 101, 192));
        button.setBackground(background);
        int size = dp(prefs.getInt("size", 64));
        params = new WindowManager.LayoutParams(size, size,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.x = dp(16);
        params.y = dp(120);
        button.setOnClickListener(v -> click());
        int slop = ViewConfiguration.get(this).getScaledTouchSlop();
        button.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = event.getRawX(); downY = event.getRawY();
                    initialX = params.x; initialY = params.y; dragging = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - downX, dy = event.getRawY() - downY;
                    if (Math.hypot(dx, dy) > slop) dragging = true;
                    if (dragging) {
                        params.x = initialX + Math.round(dx);
                        params.y = initialY + Math.round(dy);
                        updateWindow();
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!dragging) view.performClick();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    dragging = false;
                    return true;
                default: return true;
            }
        });
        windows.addView(button, params);
    }

    private void click() {
        if (busy || destroyed) return;
        busy = true;
        button.setAlpha(0.55f);
        String method = prefs.getString("method", "root");
        if (!"shizuku".equals(method)) { execute(method); return; }
        try {
            if (!Shizuku.pingBinder() || Shizuku.getVersion() < 12
                    || Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                finish(t(R.string.shizuku_access));
                return;
            }
            if (remote != null && remote.asBinder().isBinderAlive()) {
                execute(method);
            } else {
                pending = true;
                if (!bound) bindShizuku();
                else if (remote != null) {
                    disconnect();
                    bindShizuku();
                }
            }
        } catch (RuntimeException e) {
            pending = false;
            main.removeCallbacks(bindTimeout);
            disconnect();
            finish(e.toString());
        }
    }

    private void execute(String method) {
        IShellService service = remote;
        executor.execute(() -> {
            String result;
            try {
                result = "shizuku".equals(method)
                        ? (service == null ? t(R.string.disconnected) : service.execute())
                        : ShellRunner.run("root".equals(method));
                if (result == null) result = t(R.string.empty_reply);
            } catch (Exception e) { result = e.toString(); }
            String message = result;
            main.post(() -> finish(message));
        });
    }

    private void finish(String error) {
        if (destroyed) return;
        busy = false;
        if (button != null) button.setAlpha(1f);
        prefs.edit().putString("last_error", error).apply();
        if (error.isEmpty()) toast(t(R.string.success));
        else {
            Log.e("ZHX", error);
            if ("TIMEOUT".equals(error)) error = t(R.string.command_timeout);
            else if ("CANCELLED".equals(error)) error = t(R.string.cancelled);
            else if (error.startsWith("LAUNCH_FAILED:")) error = t(R.string.launch_failed) + error.substring("LAUNCH_FAILED:".length());
            else if (error.startsWith("TARGET_NOT_FOUND:")) error = t(R.string.target_not_found);
            else if (error.startsWith("HOME_FAILED:")) error = t(R.string.home_failed);
            toast(t(R.string.error) + ": " + error.substring(0, Math.min(error.length(), 180)));
        }
    }

    private void toast(String message) {
        Toast toast = Toast.makeText(this, message, Toast.LENGTH_LONG);
        if (Build.VERSION.SDK_INT < 30) toast.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, dp(64));
        toast.show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void updateWindow() {
        if (button == null || !button.isAttachedToWindow()) return;
        Point display = new Point();
        windows.getDefaultDisplay().getSize(display);
        params.x = Math.max(0, Math.min(params.x, Math.max(0, display.x - params.width)));
        params.y = Math.max(0, Math.min(params.y, Math.max(0, display.y - params.height)));
        try { windows.updateViewLayout(button, params); }
        catch (RuntimeException e) { stopSelf(); }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences preferences, String key) {
        if ("language".equals(key)) updateNotification();
        if ("method".equals(key)) { warmShizuku(); warmRoot(); }
        if ("size".equals(key) && params != null) {
            params.width = params.height = dp(preferences.getInt("size", 64));
            updateWindow();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        updateWindow();
    }

    private void disconnect() {
        main.removeCallbacks(bindTimeout);
        remote = null;
        if (bound) {
            bound = false;
            try { Shizuku.unbindUserService(userArgs, connection, true); }
            catch (RuntimeException ignored) { /* Shizuku may already have stopped. */ }
        }
    }

    @Override
    public void onDestroy() {
        destroyed = true;
        main.removeCallbacksAndMessages(null);
        prefs.unregisterOnSharedPreferenceChangeListener(this);
        executor.shutdownNow();
        ShellRunner.close();
        disconnect();
        if (button != null && button.isAttachedToWindow()) windows.removeView(button);
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
