package com.github.sandin.miniperf.server.server;

import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.SystemClock;
import android.util.Log;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Socket Server
 */
public class SocketServer {
    /**
     * log tag
     */
    private static final String TAG = "MiniPerfServer";

    /**
     * Socket timeout(connect, write, read)
     */
    private static final int SOCKET_CONNECTION_TIMEOUT_MS = 3 * 1000;

    /**
     * client max idle time(no more communication)
     */
    private static final long MAX_CONNECTION_IDLE_TIME_MS = 5 * 60 * 1000;

    /**
     * Socket Type - normal socket
     * {@link java.net.Socket}
     */
    private static final int TYPE_NORMAL_SOCKET = 1;

    /**
     * Socket Type - unix domain socket
     * {@link android.net.LocalSocket}
     */
    private static final int TYPE_LOCAL_SOCKET = 2;

    /**
     * Server State - NONE(default)
     */
    private static final int STATE_NONE = 0;

    /**
     * Server State - RUNNING
     */
    private static final int STATE_RUNNING = 1;

    /**
     * Thread pool for client connections
     */
    private final Executor mThreadPool;

    /**
     * Callback listener
     */
    public interface Callback {

        /**
         * Handle new message
         *
         * @param msg request message
         * @return response message
         */
        byte[] onMessage(ClientConnection clientConnection, byte[] msg);

    }

    /**
     * Callback
     */
    @Nullable
    private Callback mCallback;

    /**
     * Unix domain socket server
     */
    @Nullable
    private LocalServerSocket mLocalServerSocket;

    /**
     * Unix domain socket name
     */
    @Nullable
    private String mSocketName = null;

    /**
     * Normal socket server
     */
    @Nullable
    private ServerSocket mServerSocket;

    /**
     * Normal socket server pot
     */
    private int mSocketPort;

    /**
     * Socket Type
     *
     * @see SocketServer#TYPE_LOCAL_SOCKET
     * @see SocketServer#TYPE_NORMAL_SOCKET
     */
    private int mSocketType;

    /**
     * Server State
     *
     * @see SocketServer#STATE_NONE
     * @see SocketServer#STATE_RUNNING
     */
    private int mState = STATE_NONE;

    /**
     * Client connections
     *
     * NOTE: must access it with `mConnectionsLock`
     * @see SocketServer#mConnectionsLock
     */
    @NonNull
    private List<ClientConnection> mConnections = new ArrayList<>();

    /**
     * Read write lock for `mConnections`
     *
     * @see SocketServer#mConnections
     */
    private ReentrantReadWriteLock mConnectionsLock = new ReentrantReadWriteLock();

    /**
     * Create a unix domain socket server
     *
     * @param socketName unix domain socket name
     * @param messageHandler callback
     * @param maxClientNum max client number
     */
    public SocketServer(@NonNull String socketName, @Nullable Callback messageHandler, int maxClientNum) {
        mSocketName = socketName;
        mSocketType = TYPE_LOCAL_SOCKET;
        mCallback = messageHandler;

        mThreadPool = Executors.newFixedThreadPool(maxClientNum);
    }

    /**
     * Create a normal socket server
     *
     * @param socketPort socket port to listen
     * @param messageHandler callback
     * @param maxClientNum max client number
     */
    public SocketServer(int socketPort, @Nullable Callback messageHandler, int maxClientNum) {
        mSocketPort = socketPort;
        mSocketType = TYPE_NORMAL_SOCKET;
        mCallback = messageHandler;

        mThreadPool = Executors.newFixedThreadPool(maxClientNum);
    }

    /**
     * Set message handler
     *
     * @param handler handler
     */
    public void setMessageHandler(Callback handler) {
        mCallback = handler;
    }

    /**
     * Start the server, block forever
     */
    public void start() {
        if (mState == STATE_RUNNING) {
            throw new IllegalStateException("started");
        }

        mState = STATE_RUNNING;
        try {
            startConnectionMonitorThread();
            createServerSocket();
            while (true) {
                ClientConnection connection = acceptAndCreateNewConnection();
                if (connection != null) {
                    Log.i(TAG, "server got new connection, client address=" + connection.getClientName());
                    mThreadPool.execute(connection); // handle connection
                    mConnectionsLock.writeLock().lock();
                    try {
                        mConnections.add(connection);
                    } finally {
                        mConnectionsLock.writeLock().unlock();
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (mLocalServerSocket != null) {
                    mLocalServerSocket.close();
                }
            } catch (IOException ignore) {
            }
        }
    }

    public void stop() {
        // TODO: need to stop this server
    }

    /**
     * Create ServerSocket
     */
    private void createServerSocket() throws IOException {
        if (mSocketType == TYPE_NORMAL_SOCKET) {
            mServerSocket = new ServerSocket(mSocketPort);
            Log.i(TAG, "server start listening..., port=" + mSocketPort);
        } else if (mSocketType == TYPE_LOCAL_SOCKET) {
            mLocalServerSocket = new LocalServerSocket(mSocketName);
            Log.i(TAG, "server start listening..., unix domain socket name=" + mSocketName);
        } else {
            throw new IllegalStateException("unknown server socket type");
        }
    }

    /**
     * Start a thread to monitor all connections, kill the client when no more communication
     */
    private void startConnectionMonitorThread() {
        new Thread() {
            @Override
            public void run() {
                while (true) {
                    mConnectionsLock.readLock().lock();
                    try {
                        Iterator<ClientConnection> it = mConnections.iterator();
                        while (it.hasNext()) {
                            ClientConnection connection = it.next();
                            if (connection != null) {
                                if (!connection.isConnected()) {
                                    Log.w(TAG, "The connection is not connected, remove it from connection pool, client=" + connection.getClientName());
                                    it.remove();
                                } else if (connection.getIdleTime() > MAX_CONNECTION_IDLE_TIME_MS) {
                                    Log.w(TAG, "The connection is no long activated, force close and remove it from connection pool! client=" + connection.getClientName());
                                    connection.close();
                                    it.remove();
                                }
                            }
                        }
                    } finally {
                        mConnectionsLock.readLock().unlock();
                    }
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }.start(); // TODO: need to stop this thread
    }

    /**
     * Wait and create new connection for client
     *
     * @return new connection
     */
    private ClientConnection acceptAndCreateNewConnection() {
        try {
            if (mSocketType == TYPE_NORMAL_SOCKET) {
                Socket socket = mServerSocket.accept();
                return new ClientConnection(socket, mCallback);
            } else if (mSocketType == TYPE_LOCAL_SOCKET) {
                LocalSocket socket = mLocalServerSocket.accept();
                return new ClientConnection(socket, mCallback);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Client Connection
     */
    public static class ClientConnection implements Runnable {

        @Nullable
        private LocalSocket mLocalSocket;

        @Nullable
        private Socket mSocket;

        @NonNull
        private final DataInputStream mSocketInputStream;
        @NonNull
        private final DataOutputStream mSocketOutputStream;

        @Nullable
        private final Callback mCallback;

        private long mLastActiveTime;

        public ClientConnection(@NonNull LocalSocket socket, Callback callback) throws IOException {
            mLocalSocket = socket;
            mLocalSocket.setSoTimeout(SOCKET_CONNECTION_TIMEOUT_MS);
            mCallback = callback;

            mLastActiveTime = SystemClock.uptimeMillis();
            mSocketInputStream = new DataInputStream(socket.getInputStream());
            mSocketOutputStream = new DataOutputStream(socket.getOutputStream());
        }

        public ClientConnection(@NonNull Socket socket, Callback callback) throws IOException {
            mSocket = socket;
            mSocket.setSoTimeout(SOCKET_CONNECTION_TIMEOUT_MS);
            mCallback = callback;

            mLastActiveTime = SystemClock.uptimeMillis();
            mSocketInputStream = new DataInputStream(mSocket.getInputStream());
            mSocketOutputStream = new DataOutputStream(mSocket.getOutputStream());
        }

        /**
         * Get client socket's name
         */
        public String getClientName() {
            if (mSocket != null) {
                SocketAddress socketAddress = mSocket.getLocalSocketAddress();
                if (socketAddress != null) {
                    return socketAddress.toString();
                }
            } else if (mLocalSocket != null) {
                LocalSocketAddress socketAddress = mLocalSocket.getLocalSocketAddress();
                if (socketAddress != null) {
                    return socketAddress.getName();
                }
            }
            return toString();
        }

        /**
         * Returns the connection state of the socket.
         */
        public boolean isConnected() {
            if (mSocket != null) {
                return mSocket.isConnected();
            } else if (mLocalSocket != null) {
                return mLocalSocket.isConnected();
            } else {
                return false;
            }
        }

        @Override
        public void run() {
            Log.i(TAG, "server create a new thread for connection, client address=" + getClientName());
            int errorCount = 0;
            while (errorCount < 10) {
                try {
                    byte[] request = readMessage(); // block op
                    byte[] response = handleRequestMessage(this, request);
                    if (response != null) {
                        sendMessage(response);
                    }
                    mLastActiveTime = SystemClock.uptimeMillis();
                    errorCount = 0;
                } catch (SocketTimeoutException e) {
                    e.printStackTrace();
                } catch (EOFException e) {
                    Log.e(TAG, "close connection: " + e.getMessage());
                    close(); // close connection
                    break;
                } catch (IOException e) {
                    Log.e(TAG, "try again: " + e.getMessage());
                    e.printStackTrace();
                    errorCount++;
                    //close(); // close connection
                    //break;
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        }

        /**
         * Close the connection
         */
        public void close() {
            if (mSocket != null) {
                try {
                    mSocket.close();
                } catch (IOException ignore) {
                }
                mSocket = null;
            } else if (mLocalSocket != null) {
                try {
                    mLocalSocket.close();
                } catch (IOException ignore) {
                }
                mLocalSocket = null;
            }
        }

        /**
         * Get client idle time(ms)
         */
        public long getIdleTime() {
            return SystemClock.uptimeMillis() - mLastActiveTime;
        }

        /**
         * Read a message from the client
         *
         * @return message
         */
        private byte[] readMessage() throws IOException {
            Log.v(TAG, "try to read message, client=" + this.getClientName());
            int length = mSocketInputStream.readInt();
            Log.v(TAG, "try to read message length: " + length);
            byte[] buffer = new byte[length];
            mSocketInputStream.readFully(buffer);
            Log.v(TAG, "recv raw message, length=" + length);
            return buffer;
        }

        /**
         * Send a message to the client
         *
         * @param message message
         */
        public void sendMessage(@NonNull byte[] message)  {
            try {
                Log.v(TAG, "send raw message, length=" + message.length);
                mSocketOutputStream.writeInt(message.length);
                mSocketOutputStream.write(message);
                mSocketOutputStream.flush();
            } catch (IOException e) {
                Log.e(TAG, "close connection: " + e.getMessage());
                close();
            }
        }

        /**
         * Handle client request
         *
         * @param request request message
         * @return response message
         */
        private byte[] handleRequestMessage(@NonNull ClientConnection clientConnection, @NonNull byte[] request) {
            if (request.length == 4
                    && request[0] == 'p'
                    && request[1] == 'i'
                    && request[2] == 'n'
                    && request[3] == 'g'
            ) {
                return "pong".getBytes();
            }

            if (mCallback != null) {
                return mCallback.onMessage(clientConnection, request);
            }
            return null;
        }
    }

}
