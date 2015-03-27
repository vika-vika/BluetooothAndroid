/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.BluetoothChat;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.example.android.BluetoothChat.threads.AcceptThread;
import com.example.android.BluetoothChat.threads.CommunicationThread;
import com.example.android.BluetoothChat.threads.ConnectThread;

/**
 * This class does all the work for setting up and managing Bluetooth
 * connections with other devices. It has a thread that listens for
 * incoming connections, a thread for connecting with a device, and a
 * thread for performing data transmissions when connected.
 */
public class BluetoothChatService implements Settings{
    // Debugging
    private static final String TAG = "BluetoothChatService";
    private static final boolean D = true;

    // Member fields
    private final Handler UIHandler;
    private AcceptThread mSecureAcceptThread;
    private AcceptThread mInsecureAcceptThread;
    private ConnectThread mConnectThread;
    private CommunicationThread mConnectedThread;
    private int mState;

    public static final int MSG_CONNECTION_FAILED     = 0;
    public static final int MSG_START_COMMUNICATION_THREAD_OUT = 1; // remote initiates the connection
    public static final int MSG_START_COMMUNICATION_THREAD_IN  = 2; // we initiate the connection
    public static final int MSG_CONNECTION_LOST       = 3;
    public static final int MSG_SEND_INC_TEXT_TO_UI   = 4;
    public static final int MSG_SEND_OUT_TEXT_TO_UI   = 5;

    private BluetoothDevice device;
    private boolean isSecure;


    public BluetoothChatService(Context context, Handler handler) {
        mState = STATE_NONE;
        UIHandler = handler;
    }

    final Handler BluetoothCommHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {

                case MSG_CONNECTION_FAILED:
                    connectionFailed();
                    break;
                case MSG_CONNECTION_LOST:
                    connectionLost();
                    break;
                case MSG_START_COMMUNICATION_THREAD_OUT:

                    startComm(msg, false);
                    break;
                case MSG_START_COMMUNICATION_THREAD_IN:

                    startComm(msg, true);
                    break;
                case MSG_SEND_INC_TEXT_TO_UI:

                    byte[] buffer = (byte[]) msg.obj;
                    int bytes = msg.arg1;
                    UIHandler.obtainMessage(BluetoothChat.MESSAGE_READ, bytes, -1, buffer) .sendToTarget();
                    break;
                case MSG_SEND_OUT_TEXT_TO_UI:

                    buffer = (byte[]) msg.obj;
                    UIHandler.obtainMessage(BluetoothChat.MESSAGE_WRITE, -1, -1, buffer).sendToTarget();
                    break;
            }
        }
    };

    private void startComm(Message msg, boolean isWeInitiateConn){
        synchronized (BluetoothChatService.this) {
            mConnectThread = null;
        }

        String mSocketType = isSecure ? "Secure" : "Insecure";
        BluetoothSocket mmSocket = (BluetoothSocket) msg.obj;
        if (!isWeInitiateConn) {
            this.device = mmSocket.getRemoteDevice();
        }
        connected(mmSocket, device, mSocketType);
    }

    /**
     * Set the current state of the chat connection
     * @param state  An integer defining the current connection state
     */
    private synchronized void setState(int state) {
        if (D) Log.d(TAG, "setState() " + mState + " -> " + state);
        mState = state;


        if ( mSecureAcceptThread != null) {
            mSecureAcceptThread.setState(state);
        }

        if (mInsecureAcceptThread != null) {
            mInsecureAcceptThread.setState(state);
        }

        // Give the new state to the Handler so the UI Activity can update
        UIHandler.obtainMessage(BluetoothChat.MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
    }

    /**
     * Return the current connection state. */
    public synchronized int getState() {
        return mState;
    }

    /**
     * Start the chat service. Specifically start AcceptThread to begin a
     * session in listening (server) mode. Called by the Activity onResume() */
    public synchronized void start() {
        if (D) Log.d(TAG, "start service");

        // Cancel any thread attempting to make a connection
        if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}

        setState(STATE_LISTEN);

        // Start the thread to listen on a BluetoothServerSocket
        if (mSecureAcceptThread == null) {
              mSecureAcceptThread = new AcceptThread(BluetoothCommHandler, true);
            mSecureAcceptThread.start();
        }
        if (mInsecureAcceptThread == null) {
            mInsecureAcceptThread = new AcceptThread(BluetoothCommHandler, false);
            mInsecureAcceptThread.start();
        }
    }

    /**
     * Start the ConnectThread to initiate a connection to a remote device.
     * @param device  The BluetoothDevice to connect
     * @param secure Socket Security type - Secure (true) , Insecure (false)
     */
    public synchronized void connect(BluetoothDevice device, boolean secure) {
        
        this.device = device;
        this.isSecure = secure;

        if (D) Log.d(TAG, "connect to: " + device);

        // Cancel any thread attempting to make a connection
        if (mState == STATE_CONNECTING) {
            if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}

        // Start the thread to connect with the given device
        mConnectThread = new ConnectThread(BluetoothCommHandler, device, secure);
        mConnectThread.start();
        setState(STATE_CONNECTING);
    }

    /**
     * Start the ConnectedThread to begin managing a Bluetooth connection
     * @param socket  The BluetoothSocket on which the connection was made
     * @param device  The BluetoothDevice that has been connected
     */
    public synchronized void connected(BluetoothSocket socket, BluetoothDevice
            device, final String socketType) {
        if (D) Log.d(TAG, "connected, Socket Type:" + socketType);

        // Cancel the thread that completed the connection
        if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}

        // Cancel the accept thread because we only want to connect to one device
        if (mSecureAcceptThread != null) {
            mSecureAcceptThread.cancel();
            mSecureAcceptThread = null;
        }
        if (mInsecureAcceptThread != null) {
            mInsecureAcceptThread.cancel();
            mInsecureAcceptThread = null;
        }

        // Start the thread to manage the connection and perform transmissions
        mConnectedThread = new CommunicationThread(BluetoothCommHandler, socket, socketType);
        mConnectedThread.start();

        // Send the name of the connected device back to the UI Activity
        Message msg = UIHandler.obtainMessage(BluetoothChat.MESSAGE_DEVICE_NAME);
        Bundle bundle = new Bundle();
        bundle.putString(BluetoothChat.DEVICE_NAME, device.getName());
        msg.setData(bundle);
        UIHandler.sendMessage(msg);

        setState(STATE_CONNECTED);
    }

    /**
     * Stop all threads
     */
    public synchronized void stop() {
        if (D) Log.d(TAG, "stop");

        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        if (mSecureAcceptThread != null) {
            mSecureAcceptThread.cancel();
            mSecureAcceptThread = null;
        }

        if (mInsecureAcceptThread != null) {
            mInsecureAcceptThread.cancel();
            mInsecureAcceptThread = null;
        }
        setState(STATE_NONE);
    }

    /**
     * Write to the ConnectedThread in an unsynchronized manner
     * @param out The bytes to write
     * @see com.example.android.BluetoothChat.threads.CommunicationThread#write(byte[])
     */
    public void write(byte[] out) {
        // Create temporary object
        CommunicationThread r;
        // Synchronize a copy of the ConnectedThread
        synchronized (this) {
            if (mState != STATE_CONNECTED) return;
            r = mConnectedThread;
        }
        // Perform the write unsynchronized
        r.write(out);
    }

    /**
     * Indicate that the connection attempt failed and notify the UI Activity.
     */
    private void connectionFailed() {
        // Send a failure message back to the Activity
        Message msg = UIHandler.obtainMessage(BluetoothChat.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(BluetoothChat.TOAST, "Unable to connect device");
        msg.setData(bundle);
        UIHandler.sendMessage(msg);

        // Start the service over to restart listening mode
        BluetoothChatService.this.start();
    }

    /**
     * Indicate that the connection was lost and notify the UI Activity.
     */
    private void connectionLost() {
        // Send a failure message back to the Activity
        Message msg = UIHandler.obtainMessage(BluetoothChat.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(BluetoothChat.TOAST, "Device connection was lost");
        msg.setData(bundle);
        UIHandler.sendMessage(msg);

        // Start the service over to restart listening mode
        BluetoothChatService.this.start();
    }
}
