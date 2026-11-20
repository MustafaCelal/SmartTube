package com.liskovsoft.smartyoutubetv2.common.misc;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.liskovsoft.smartyoutubetv2.common.R;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * HomeTube: app log kept in files inside the app's own folder (no permission needed).<br/>
 * Records everything written with the shared Log class, every on-screen message, screen changes and crashes.<br/>
 * Location: Android/data/&lt;package&gt;/files/logs/hometube.log (older parts: hometube.1.log ... hometube.3.log).
 */
public final class HomeTubeLog {
    private static final String TAG = "HomeTube";
    private static final String DIR_NAME = "logs";
    private static final String FILE_NAME = "hometube";
    private static final long MAX_FILE_BYTES = 1024 * 1024;
    private static final int MAX_OLD_FILES = 3;
    private static final long FLUSH_DELAY_MS = 1_000;
    private static final Object sLock = new Object();
    private static final SimpleDateFormat sTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
    private static volatile boolean sEnabled;
    private static File sDir;
    private static File sFile;
    private static BufferedWriter sWriter;
    private static long sFileSize;
    private static Handler sHandler;
    private static final Runnable sFlush = HomeTubeLog::flushInt;

    private HomeTubeLog() {
    }

    /**
     * Call once from Application.onCreate. Does nothing outside HomeTube.
     */
    public static void init(@NonNull Application app) {
        if (sEnabled || !app.getResources().getBoolean(R.bool.hometube_rail_actions)) {
            return;
        }

        File base = app.getExternalFilesDir(null);
        if (base == null) {
            base = app.getFilesDir();
        }
        sDir = new File(base, DIR_NAME);
        sFile = new File(sDir, FILE_NAME + ".log");

        HandlerThread thread = new HandlerThread("HomeTubeLog");
        thread.start();
        sHandler = new Handler(thread.getLooper());
        sEnabled = true;

        event("==== App start: " + getVersion(app) + " | " + Build.MANUFACTURER + " " + Build.MODEL +
                " | Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");

        installLogger();
        installCrashHandler();
        app.registerActivityLifecycleCallbacks(new ScreenTracker());
    }

    public static boolean isEnabled() {
        return sEnabled;
    }

    /**
     * @param level one of V, D, I, W, E
     */
    public static void write(char level, @Nullable String tag, @Nullable String msg) {
        if (!sEnabled || msg == null) {
            return;
        }

        if (isNoisy(level, tag, msg)) {
            return;
        }

        String line = formatLine(level, tag, msg);
        sHandler.post(() -> append(line, level == 'E'));
    }

    public static void event(@NonNull String msg) {
        write('I', TAG, msg);
    }

    /**
     * Message shown on the screen (toast).
     */
    public static void message(@Nullable String msg, boolean isLong) {
        if (msg != null && !msg.isEmpty()) {
            write('I', "Message", (isLong ? "[long] " : "") + msg.replace('\n', ' '));
        }
    }

    @Nullable
    public static File getDir() {
        return sDir;
    }

    /**
     * Lines that come many times per second and would push everything else out of the size limit.
     */
    private static boolean isNoisy(char level, @Nullable String tag, String msg) {
        if (tag == null) {
            return false;
        }

        if (tag.startsWith("Sabr")) {
            // routine stream lines repeat every few seconds; real stream errors are kept
            return level != 'E' || msg.startsWith("Unknown part encountered") || msg.endsWith("disposing, result=-1");
        }

        return level == 'D' && (tag.equals("JsonPathTypeAdapter") || tag.equals("ScreensaverManager") ||
                tag.startsWith("ExoPlayerImpl") || tag.startsWith("Glide"));
    }

    private static String formatLine(char level, @Nullable String tag, String msg) {
        String time;
        synchronized (sTimeFormat) {
            time = sTimeFormat.format(new Date());
        }
        return time + " " + level + "/" + (tag != null ? tag : "?") + ": " + msg;
    }

    private static void append(String line, boolean flushNow) {
        synchronized (sLock) {
            try {
                if (sWriter == null || sFileSize > MAX_FILE_BYTES) {
                    openFile();
                }

                sWriter.write(line);
                sWriter.write('\n');
                sFileSize += line.length() + 1;

                if (flushNow) {
                    sWriter.flush();
                } else {
                    sHandler.removeCallbacks(sFlush);
                    sHandler.postDelayed(sFlush, FLUSH_DELAY_MS);
                }
            } catch (IOException e) {
                android.util.Log.e(TAG, "Can't write the log: " + e.getMessage());
                closeQuietly();
            }
        }
    }

    private static void openFile() throws IOException {
        closeQuietly();

        if (!sDir.exists() && !sDir.mkdirs()) {
            throw new IOException("Can't create " + sDir);
        }

        if (sFile.exists() && sFile.length() > MAX_FILE_BYTES) {
            rotate();
        }

        sWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(sFile, true), StandardCharsets.UTF_8));
        sFileSize = sFile.length();
    }

    private static void rotate() {
        File oldest = new File(sDir, FILE_NAME + "." + MAX_OLD_FILES + ".log");
        if (oldest.exists()) {
            oldest.delete();
        }

        for (int i = MAX_OLD_FILES - 1; i >= 1; i--) {
            File file = new File(sDir, FILE_NAME + "." + i + ".log");
            if (file.exists()) {
                file.renameTo(new File(sDir, FILE_NAME + "." + (i + 1) + ".log"));
            }
        }

        sFile.renameTo(new File(sDir, FILE_NAME + ".1.log"));
    }

    private static void flushInt() {
        synchronized (sLock) {
            if (sWriter != null) {
                try {
                    sWriter.flush();
                } catch (IOException e) {
                    closeQuietly();
                }
            }
        }
    }

    private static void closeQuietly() {
        if (sWriter != null) {
            try {
                sWriter.close();
            } catch (IOException e) {
                // NOP
            }
            sWriter = null;
        }
    }

    /**
     * Route the shared Log class (used everywhere in the app and libraries) through the file too.
     */
    private static void installLogger() {
        try {
            Field field = com.liskovsoft.sharedutils.mylogger.Log.class.getDeclaredField("sLogger");
            field.setAccessible(true);
            field.set(null, com.liskovsoft.sharedutils.mylogger.HomeTubeLogger.create());
        } catch (Exception e) {
            event("Can't hook the shared logger: " + e);
        }
    }

    private static void installCrashHandler() {
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();

        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            // Written right here: the process may die before the log thread runs
            StringWriter trace = new StringWriter();
            error.printStackTrace(new PrintWriter(trace));
            append(formatLine('E', "Crash", "Thread " + thread.getName() + ": " + trace), true);

            if (previous != null) {
                previous.uncaughtException(thread, error);
            }
        });
    }

    private static String getVersion(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return info.packageName + " " + info.versionName;
        } catch (Exception e) {
            return context.getPackageName();
        }
    }

    private static final class ScreenTracker implements Application.ActivityLifecycleCallbacks {
        @Override
        public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
            event("Screen created: " + activity.getClass().getSimpleName());
        }

        @Override
        public void onActivityStarted(@NonNull Activity activity) {
        }

        @Override
        public void onActivityResumed(@NonNull Activity activity) {
            event("Screen shown: " + activity.getClass().getSimpleName());
        }

        @Override
        public void onActivityPaused(@NonNull Activity activity) {
            event("Screen hidden: " + activity.getClass().getSimpleName());
        }

        @Override
        public void onActivityStopped(@NonNull Activity activity) {
            flushInt();
        }

        @Override
        public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
        }

        @Override
        public void onActivityDestroyed(@NonNull Activity activity) {
            event("Screen closed: " + activity.getClass().getSimpleName());
        }
    }
}
