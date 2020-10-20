package com.github.sandin.miniperfserver;

import android.content.Context;

import androidx.test.espresso.core.internal.deps.guava.base.Predicate;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.github.sandin.miniperfserver.bean.TargetApp;
import com.github.sandin.miniperfserver.monitor.PerformanceMonitor;
import com.github.sandin.miniperfserver.util.AndroidProcessUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertTrue;

/**
 * Instrumented test, which will execute on an Android device.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
public class PerformanceMonitorTest {
    private static final String TARGET_PACKAGE_NAME = "com.github.sandin.miniperfserver";

    private Context mContext;
    private PerformanceMonitor mPerformanceMonitor;
    private TargetApp mTargetApp;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mPerformanceMonitor = new PerformanceMonitor(mContext, 1000, 2 * 1000);

        int pid = AndroidProcessUtils.getPid(mContext, TARGET_PACKAGE_NAME);
        assertTrue(pid != -1);
        mTargetApp = new TargetApp(TARGET_PACKAGE_NAME, pid);
    }

    @After
    public void tearDown() {
        mPerformanceMonitor = null;
    }

    @Test
    public void start() throws InterruptedException {
        mPerformanceMonitor.start(mTargetApp);

        Thread.sleep(10 * 1000);

        mPerformanceMonitor.stop();
    }

}