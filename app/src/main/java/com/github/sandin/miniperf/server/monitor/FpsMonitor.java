package com.github.sandin.miniperf.server.monitor;

import android.util.Log;

import androidx.annotation.NonNull;

import com.github.sandin.miniperf.server.bean.FpsInfo;
import com.github.sandin.miniperf.server.bean.JankInfo;
import com.github.sandin.miniperf.server.bean.TargetApp;
import com.github.sandin.miniperf.server.proto.FPS;
import com.github.sandin.miniperf.server.proto.FrameTime;
import com.github.sandin.miniperf.server.proto.ProfileNtf;
import com.github.sandin.miniperf.server.proto.ProfileReq;
import com.github.sandin.miniperf.server.util.ReadSystemInfoUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

//TODO bug
public class FpsMonitor implements IMonitor<FpsInfo> {

    private static final String TAG = "FpsMonitor";
    private final static String SERVICE_NAME = "SurfaceFlinger";
    private String mLayerName;
    private long mRefreshPeriod;
    private long mLatestSeen = 0;
    private List<Long> mElapsedTimes = new ArrayList<>();
    private Set<ProfileReq.DataType> mNeedDataTypes = new LinkedHashSet<>();
    private long mLastTime = 0;

    private boolean getLayerName(@NonNull String packageName) {
        List<String> result = ReadSystemInfoUtils.readInfoFromDumpsys(SERVICE_NAME, new String[]{"--list"});
        for (String line : result) {
            if (line.startsWith(packageName + "/")) {
                mLayerName = line.trim();
                return true;
            }
        }
        return false;
    }

    private List<String> getFramesDataFromDumpsys(@NonNull String layerName) {
        List<String> timestamps = new LinkedList<>();
        if (layerName != null) {
            //单位为ns
            timestamps = ReadSystemInfoUtils.readInfoFromDumpsys(SERVICE_NAME, new String[]{"--latency", mLayerName});
            if (timestamps.size() > 0) {
                mRefreshPeriod = Long.parseLong(timestamps.get(0));
                Log.i(TAG, "refresh period is " + mRefreshPeriod);
            }
            Log.i(TAG, "collect frame data from dumpsys success : " + timestamps.toString());
        }
        return timestamps;
    }

    /*
    https://cs.android.com/android/platform/superproject/+/master:tools/test/graphicsbenchmark/performance_tests/hostside/src/com/android/game/qualification/metric/GameQualificationFpsCollector.java;drc=master;l=181?q=GameQualificationFps&ss=android
    */
    private boolean sample(long readyTimeStamp, long presentTimeStamp) {
        if (presentTimeStamp == Long.MAX_VALUE || readyTimeStamp == Long.MAX_VALUE) {
            return false;
        } else if (presentTimeStamp < mLatestSeen) {
            return false;
        } else if (presentTimeStamp == mLatestSeen) {
            return true;
        } else {
            mElapsedTimes.add(presentTimeStamp);
            mLatestSeen = presentTimeStamp;
            return false;
        }
    }

    private List<Long> getNewFrameTimes(@NonNull String packageName) {
        boolean hasLayerName = getLayerName(packageName);
        if (!hasLayerName) {
            Log.e(TAG, "application hasn't start or package name is error!");
            return null;
        }
        List<String> framesData = getFramesDataFromDumpsys(mLayerName);
        Log.i(TAG, "frame times size :  " + framesData.size());
        Log.i(TAG, "now last time is : " + mLastTime);
        if (framesData.size() == 1) {
            Log.e(TAG, "can't get frames data !");
            return null;
        }
        boolean overlap = false;
        for (String line : framesData) {
            String[] parts = line.split("\t");
            if (parts.length == 3) {
                //get frame times from dumpsys
                if (sample(Long.parseLong(parts[2]), Long.parseLong(parts[1]))) {
                    overlap = true;
                }
            }
        }
        if (!overlap)
            Log.e(TAG, "No overlap with previous poll, we missed some frames!");
        Log.i(TAG, "Elapsed times size : " + mElapsedTimes.size());
        if (mLastTime == 0) {
            mLastTime = mElapsedTimes.get(mElapsedTimes.size() - 1);
            return null;
        } else {
            List<Long> newFrameTimes = new ArrayList<>();
            newFrameTimes.add(mLastTime);
            for (long time : mElapsedTimes) {
                if (time > mLastTime) {
                    newFrameTimes.add(time);
                }
            }
            return newFrameTimes;
        }
    }

    //TODO 存在单位问题
    private JankInfo checkJank(List<Long> frameTimes) {
        JankInfo jankInfo = new JankInfo();
        int jank = 0;
        int bigJank = 0;
        Long first_3s_frame_time = null;
        Long first_2s_frame_time = null;
        Long first_1s_frame_time = null;
        for (Long frameTime : frameTimes) {
            Double time = (double) frameTime;
            if (first_1s_frame_time != null || first_2s_frame_time != null || first_3s_frame_time != null) {
                double average = (first_1s_frame_time + first_2s_frame_time + first_3s_frame_time) / (3.0 * 2.0) + 2.0;
                if ((average > 0) && (time > 85.33333333333333)) {
                    jank++;
                    if (time.compareTo(12700.0) > 0)
                        bigJank++;
                    first_1s_frame_time = null;
                    first_2s_frame_time = null;
                    first_3s_frame_time = null;
                }
            } else {
                first_3s_frame_time = first_2s_frame_time;
                first_2s_frame_time = first_1s_frame_time;
                first_1s_frame_time = frameTime;
            }
        }
        jankInfo.setJank(jank);
        jankInfo.setBigJank(bigJank);
        return jankInfo;
    }

    public boolean addNeedDataType(ProfileReq.DataType dataType) {
        boolean result = mNeedDataTypes.add(dataType);
        return result;
    }

    @Override
    public FpsInfo collect(TargetApp targetApp, long timestamp, ProfileNtf.Builder data) throws Exception {
        Log.i(TAG, "start collect fps info");
        FpsInfo fpsInfo = new FpsInfo();
        List<Long> newFrameTimes = getNewFrameTimes(targetApp.getPackageName());
        Log.i(TAG, "collect new frame times success : " + newFrameTimes);
        mElapsedTimes.clear();
        if (newFrameTimes == null) {
            Log.v(TAG, "no refresh!");
            fpsInfo.setFps(FPS.newBuilder().build());
            fpsInfo.setFrameTime(FrameTime.newBuilder().build());
            return fpsInfo;
        }

        List<Long> frameTimes = new LinkedList<>();
        for (int i = 1; i < newFrameTimes.size(); i++) {
            //TODO
            frameTimes.add((newFrameTimes.get(i) - newFrameTimes.get(i - 1)) / 10000);
        }

        JankInfo jankInfo = checkJank(frameTimes);
        float fps = newFrameTimes.size() / ((newFrameTimes.get(newFrameTimes.size() - 1) - newFrameTimes.get(0)) / (float) 1e9);
        Log.i(TAG, "collect fps success : " + fps);
        fpsInfo.setFps(FPS.newBuilder().setFps(fps).build());
        fpsInfo.setFrameTime(FrameTime.newBuilder().addAllFrameTime(frameTimes).build());
        if (data != null) {
            if (mNeedDataTypes.contains(ProfileReq.DataType.FPS))
                data.setFps(FPS.newBuilder().setFps(fps).setJank(jankInfo.getJank()).setBigJank(jankInfo.getBigJank()));
            if (mNeedDataTypes.contains(ProfileReq.DataType.FRAME_TIME))
                data.setFrameTime(FrameTime.newBuilder().addAllFrameTime(frameTimes));
        }
        Log.i(TAG, "collect fps info success : " + fpsInfo.toString());
        //clear cache
        return fpsInfo;
    }

}