package com.github.sandin.miniperf.server.session;

import androidx.annotation.NonNull;

import com.github.sandin.miniperf.server.bean.TargetApp;
import com.github.sandin.miniperf.server.monitor.PerformanceMonitor;
import com.github.sandin.miniperf.server.proto.MiniPerfServerProtocol;
import com.github.sandin.miniperf.server.proto.ProcessNotFoundNTF;
import com.github.sandin.miniperf.server.proto.ProfileNtf;
import com.github.sandin.miniperf.server.proto.ProfileReq;
import com.github.sandin.miniperf.server.server.SocketServer;

import java.util.List;

/**
 * Profile Session
 */
public final class Session implements PerformanceMonitor.Callback {

    private int mSessionId;

    private SocketServer.ClientConnection mConnection;

    private PerformanceMonitor mMonitor;

    /**
     * Session
     *
     * @param sessionId  session id
     * @param connection client connection
     * @param monitor    profile monitor
     */
    public Session(int sessionId,
                   @NonNull SocketServer.ClientConnection connection,
                   @NonNull PerformanceMonitor monitor) {
        this.mSessionId = sessionId;
        this.mConnection = connection;
        this.mMonitor = monitor;
    }

    /**
     * Start a session
     *
     * @return success/fail
     */
    public boolean start(@NonNull TargetApp targetApp,
                         @NonNull List<ProfileReq.DataType> dataTypes) {
        mMonitor.registerCallback(this);
        return mMonitor.start(targetApp, dataTypes);
    }

    /**
     * Stop the session
     */
    public void stop() {
        mMonitor.unregisterCallback(this);
        mMonitor.stop();
    }

    /**
     * implement of Monitor's Callback, bridge between {@link SocketServer.ClientConnection} and {@link PerformanceMonitor}
     * receive new data from monitor, and send it to the client
     * <p>
     * +---------+   data   +---------+   bytes  +--------+
     * | Monitor |  +-----> | Session |  +-----> | Client |
     * +---------+          +---------+          +--------+
     */
    @Override
    public void onUpdate(ProfileNtf data) {
        // TODO: Bug!!
//        if (mConnection.isConnected()) {
        MiniPerfServerProtocol response = MiniPerfServerProtocol.newBuilder().setProfileNtf(data).build();
        mConnection.sendMessage(response.toByteArray());
//        } else {
//            Log.w("MiniPerfServer", "disconnected, can not send data to client");
//            stop();
//            //disconnect
//
//        }
    }

    @Override
    public void sendAppClosedNTF(ProcessNotFoundNTF ntf) {
        MiniPerfServerProtocol response = MiniPerfServerProtocol.newBuilder().setProcessNotFoundNTF(ntf).build();
        mConnection.sendMessage(response.toByteArray());
    }


    public int getSessionId() {
        return mSessionId;
    }

    public void setSessionId(int sessionId) {
        this.mSessionId = sessionId;
    }

    public SocketServer.ClientConnection getConnection() {
        return mConnection;
    }

    public void setConnection(SocketServer.ClientConnection connection) {
        this.mConnection = connection;
    }

    public PerformanceMonitor getMonitor() {
        return mMonitor;
    }

    public void setMonitor(PerformanceMonitor monitor) {
        this.mMonitor = monitor;
    }

    @Override
    public String toString() {
        return "Session{" +
                "sessionId=" + mSessionId +
                ", connection=" + mConnection +
                ", monitor=" + mMonitor +
                '}';
    }
}
