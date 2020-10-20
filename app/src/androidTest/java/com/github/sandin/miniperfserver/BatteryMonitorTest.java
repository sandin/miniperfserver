package com.github.sandin.miniperfserver;


import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.github.sandin.miniperfserver.monitor.BatteryMonitor;
import com.github.sandin.miniperfserver.proto.Power;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

@RunWith(AndroidJUnit4.class)
public class BatteryMonitorTest {

    private Context mContext;
    private BatteryMonitor mBatteryMonitor;
    private String[] mSources = {"server", "dex"};

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mBatteryMonitor = new BatteryMonitor(mContext, null);
    }

    @After
    public void tearDown() {
        mBatteryMonitor = null;
    }

    @Test
    public void getPowerInfoFromServerTest(){

    }

    @Test
    public void collectTest() throws Exception {
        for (String source : mSources) {
            mBatteryMonitor = new BatteryMonitor(mContext, source);
            Power power = mBatteryMonitor.collect(mContext, null, 0,null);
            BatteryMonitor.dumpPower(power);
            Assert.assertNotNull(power);
        }
    }

}
