package com.liskovsoft.sharedutils.mylogger;

import com.liskovsoft.smartyoutubetv2.common.misc.HomeTubeLog;

/**
 * HomeTube: keeps the usual logcat output and also writes every line to the HomeTube log file.<br/>
 * Lives in the logger's package because the base class is package-private.
 */
public final class HomeTubeLogger extends MyLogger {
    private final MyLogger mSystem = new SystemLogger();

    private HomeTubeLogger() {
    }

    public static Object create() {
        return new HomeTubeLogger();
    }

    @Override
    public void d(String tag, String msg) {
        mSystem.d(tag, msg);
        HomeTubeLog.write('D', tag, msg);
    }

    @Override
    public void i(String tag, String msg) {
        mSystem.i(tag, msg);
        HomeTubeLog.write('I', tag, msg);
    }

    @Override
    public void w(String tag, String msg) {
        mSystem.w(tag, msg);
        HomeTubeLog.write('W', tag, msg);
    }

    @Override
    public void e(String tag, String msg) {
        mSystem.e(tag, msg);
        HomeTubeLog.write('E', tag, msg);
    }

    @Override
    public String getLogType() {
        return Log.LOG_TYPE_SYSTEM;
    }
}
