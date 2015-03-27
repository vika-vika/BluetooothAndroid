package com.example.android.BluetoothChat.threads;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.example.android.BluetoothChat.BluetoothChatService;
import com.example.android.BluetoothChat.Settings;

import java.io.IOException;


/**
 * This thread runs while listening for incoming connections. It behaves
 * like a server-side client. It runs until a connection is accepted
 * (or until cancelled).
 */
public class AcceptThread extends Thread implements Settings {

    private static final String TAG = "AcceptThread";
    private static final boolean D = true;

    // The local server socket
    private final BluetoothServerSocket mmServerSocket;
    private final Handler handler;
    private String mSocketType;
    private int mState;
    private boolean isSocketClosed;
    private Object lock = new Object();

    public AcceptThread(Handler handler, boolean secure) {
        this.handler = handler;

        BluetoothServerSocket tmp = null;
        mSocketType = secure ? "Secure":"Insecure";
        BluetoothAdapter mAdapter = BluetoothAdapter.getDefaultAdapter();
        // Create a new listening server socket
        try {
            if (secure) {
                tmp = mAdapter.listenUsingRfcommWithServiceRecord(Settings.NAME_SECURE, Settings.MY_UUID_SECURE);
            } else {
                tmp = mAdapter.listenUsingInsecureRfcommWithServiceRecord(Settings.NAME_INSECURE, Settings.MY_UUID_INSECURE);
            }
        } catch (IOException e) {
            Log.e(TAG, "Socket Type: " + mSocketType + "listen() failed", e);
        }
        mmServerSocket = tmp;
    }

    public void run() {
        if (D) Log.d(TAG, "Socket Type: " + mSocketType + "BEGIN mAcceptThread" + this);
        setName("AcceptThread" + mSocketType);
        BluetoothSocket socket = null;
        // Listen to the server socket if we're not connected

        while (mState != STATE_CONNECTED) {
            try {
                Log.d(TAG, "mmServerSocket.accept() " + isSocketClosed);
                // This is a blocking call and will only return on a
                // successful connection or an exception

                // use sync here
                 if (!isSocketClosed) {
                     socket = mmServerSocket.accept();
                 }

            } catch (IOException e) {
                // crashed when connection already established
                Log.w(TAG, "mmServerSocket failed. Connection established");
                Log.w(TAG, "Socket Type: " + mSocketType + "accept() failed", e);
                break;
            }

            // If a connection was accepted
            if (socket != null) {
                synchronized (this) {
                    switch (mState) {

                        case STATE_LISTEN:
                        case STATE_CONNECTING:
                            // Situation normal. Start the connected thread.
                          /*  Message msg = new Message();
                            msg.what = BluetoothChatService.MSG_START_COMMUNICATION_THREAD_OUT;
                            msg.obj = socket;
                            handler.sendMessage(msg);*/
                            break;
                        case STATE_NONE:
                        case STATE_CONNECTED:
                            Log.e(TAG, "connected accept thread");
                            Message msg = new Message();
                            msg.what = BluetoothChatService.MSG_START_COMMUNICATION_THREAD_OUT;
                            msg.obj = socket;
                            handler.sendMessage(msg);


                            // Either not ready or already connected. Terminate new socket.
                          /*  try {
                                socket.close();
                            } catch (IOException e) {
                                Log.e(TAG, "Could not close unwanted socket", e);
                            }*/
                            break;
                    }
                }
            }
        }
        if (D) Log.i(TAG, "END mAcceptThread, socket Type: " + mSocketType);

    }

    public void cancel() {
        if (D) Log.d(TAG, "Socket Type" + mSocketType + " cancel " + this);

        try {
            mmServerSocket.close();
            isSocketClosed = true;
        } catch (IOException e) {
            Log.e(TAG, "Socket Type" + mSocketType + "close() of server failed", e);
        }
    }

    public synchronized void setState(int state) {
        this.mState = state;
    }
}