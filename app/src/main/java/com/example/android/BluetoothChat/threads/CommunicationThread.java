package com.example.android.BluetoothChat.threads;

/**
 * Created by viktoriala on 3/27/2015.
 */

import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.example.android.BluetoothChat.BluetoothChatService;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * This thread runs during a connection with a remote device.
 * It handles all incoming and outgoing transmissions.
 */
public class CommunicationThread extends Thread {
    private static final String TAG = "CommunicationThread";

    private final BluetoothSocket mmSocket;
    private final InputStream mmInStream;
    private final OutputStream mmOutStream;
    private final Handler handler;

    public CommunicationThread(Handler handler, BluetoothSocket socket, String socketType) {
        Log.d(TAG, "create CommunicationThread: " + socketType);

        this.handler = handler;
        mmSocket = socket;
        InputStream tmpIn = null;
        OutputStream tmpOut = null;

        // Get the BluetoothSocket input and output streams
        try {
            tmpIn = socket.getInputStream();
            tmpOut = socket.getOutputStream();
        } catch (IOException e) {
            Log.e(TAG, "temp sockets not created", e);
        }

        mmInStream = tmpIn;
        mmOutStream = tmpOut;
    }

    public void run() {
        Log.i(TAG, "BEGIN mCommunicationThread");
        byte[] buffer = new byte[1024];
        int bytes;

        // Keep listening to the InputStream while connected
        while (true) {
            try {
                // Read from the InputStream
                bytes = mmInStream.read(buffer);

                // Send the obtained bytes to the UI Activity\
                Message msg = new Message();
                msg.what = BluetoothChatService.MSG_SEND_INC_TEXT_TO_UI;
                msg.arg1 = bytes;
                msg.obj = buffer;
                handler.sendMessage(msg);
               // mHandler.obtainMessage(BluetoothChat.MESSAGE_READ, bytes, -1, buffer) .sendToTarget();


            } catch (IOException e) {
                Log.w(TAG, "Communication disconnected", e);
                handler.sendEmptyMessage(BluetoothChatService.MSG_CONNECTION_LOST);
                break;
            }
        }
    }

    /**
     * Write to the connected OutStream.
     * @param buffer  The bytes to write
     */
    public void write(byte[] buffer) {
        try {
            mmOutStream.write(buffer);
            Message msg = new Message();
            msg.what = BluetoothChatService.MSG_SEND_OUT_TEXT_TO_UI;
            msg.obj = buffer;
            handler.sendMessage(msg);

            // Share the sent message back to the UI Activity
           // mHandler.obtainMessage(BluetoothChat.MESSAGE_WRITE, -1, -1, buffer).sendToTarget();
        } catch (IOException e) {
            Log.e(TAG, "Exception during write", e);
        }
    }

    public void cancel() {
        try {
            mmSocket.close();
        } catch (IOException e) {
            Log.e(TAG, "close() of connect socket failed", e);
        }
    }

}