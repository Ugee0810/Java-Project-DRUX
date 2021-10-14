package me.jfenn.alarmio.services;

import android.bluetooth.BluetoothSocket;
import android.os.SystemClock;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class Thread {
    private final BluetoothSocket mmSocket;
    private final InputStream mmInStream;
    private final OutputStream mmOutStream;

    public Thread(BluetoothSocket socket) {

        mmSocket = socket;

        InputStream tmpIn = null;
        OutputStream tmpOut = null;

        try {
            tmpIn = socket.getInputStream();
            tmpOut = socket.getOutputStream();
        } catch (IOException e) { }
        mmInStream = tmpIn;
        mmOutStream = tmpOut;
    }//ConnectedThread

    public void run() {
        byte[] buffer = new byte[1024];  // buffer store for the stream
        int bytes; // bytes returned from read()
        // Keep listening to the InputStream until an exception occurs
        while (true) {
            try {
                // Read from the InputStream
                bytes = mmInStream.available();
                if (bytes != 0) {
                    buffer = new byte[1024];
                    SystemClock.sleep(100); //pause and wait for rest of data. Adjust this depending on your sending speed.
                    bytes = mmInStream.available(); // how many bytes are ready to be read?
                    bytes = mmInStream.read(buffer, 0, bytes); // record how many bytes we actually read
                }
            } catch (IOException e) {
                e.printStackTrace();
                break;
            }
        }
    }

    public void write(String input) {
        byte[] bytes = input.getBytes();//converts entered String into bytes
        try {
            mmOutStream.write(bytes);
        }
        catch (IOException e) { }
    }//Call this from the main activity to send data to the remote device

    public void cancel() {
        try {
            mmSocket.close();
        } catch (IOException e) {
        }
    }//Call this from the main activity to shutdown the connection
}