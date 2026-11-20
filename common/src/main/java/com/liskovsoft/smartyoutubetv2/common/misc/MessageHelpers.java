package com.liskovsoft.smartyoutubetv2.common.misc;

import android.content.Context;

import com.liskovsoft.sharedutils.helpers.Helpers;

/**
 * HomeTube: same methods as the shared MessageHelpers, but every message shown on the screen
 * is also written to the HomeTube log together with the code that showed it.
 */
public final class MessageHelpers {
    private MessageHelpers() {
    }

    public static void showMessage(Context ctx, String TAG, Throwable ex) {
        showMessage(ctx, TAG + ": %s", Helpers.toString(ex));
    }

    public static void showMessage(Context ctx, String template, Object... params) {
        showMessage(ctx, String.format(template, params));
    }

    public static void showMessage(Context ctx, String msg) {
        showMessage(ctx, msg, false);
    }

    public static void showMessage(Context ctx, String msg, boolean isLong) {
        if (ctx == null || msg == null || msg.isEmpty()) {
            return;
        }

        if (HomeTubeLog.isEnabled()) {
            HomeTubeLog.message(msg + "  <- " + getCaller(), isLong);
        }

        com.liskovsoft.sharedutils.helpers.MessageHelpers.showMessage(ctx, msg, isLong);
    }

    public static void showMessage(Context ctx, int resId) {
        if (ctx != null) {
            showMessage(ctx, ctx.getResources().getString(resId));
        }
    }

    public static void showMessage(Context ctx, int resId, Object... formatArgs) {
        if (ctx != null) {
            showMessage(ctx, ctx.getResources().getString(resId, formatArgs));
        }
    }

    public static void showLongMessage(Context ctx, int resId) {
        if (ctx != null) {
            showLongMessage(ctx, ctx.getResources().getString(resId));
        }
    }

    public static void showLongMessage(Context ctx, String msg) {
        if (ctx != null) {
            showMessage(ctx, msg, true);
        }
    }

    public static void showLongMessage(Context ctx, String template, Object... params) {
        if (ctx != null) {
            showMessage(ctx, String.format(template, params), true);
        }
    }

    public static void showLongMessageEndPause(Context context, int resId) {
        showLongMessage(context, resId);

        try {
            Thread.sleep(5_000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void cancelToasts() {
        com.liskovsoft.sharedutils.helpers.MessageHelpers.cancelToasts();
    }

    /**
     * The first frame outside this class, e.g. "ErrorFixerController.java:184"
     */
    private static String getCaller() {
        for (StackTraceElement element : new Throwable().getStackTrace()) {
            if (!MessageHelpers.class.getName().equals(element.getClassName())) {
                return element.getFileName() + ":" + element.getLineNumber();
            }
        }

        return "?";
    }
}
