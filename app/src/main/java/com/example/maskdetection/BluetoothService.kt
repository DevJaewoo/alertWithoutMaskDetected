package com.example.maskdetection

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.Handler
import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*

class BluetoothService private constructor() {

    private val mBluetoothAdapter: BluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    private var mConnectThread: ConnectThread? = null
    private var mConnectedThread: ConnectedThread? = null
    private var mHandler: Handler? = null

    private var mState: Int = 0

    companion object {
        const val STATE_NONE = 0
        const val STATE_CONNECTING = 1
        const val STATE_CONNECTED = 2

        private const val TAG = "AJW"
        private const val BLUETOOTH_NAME = "Administrator"
        private val BLUETOOTH_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        private var instance: BluetoothService? = null

        fun getInstance(): BluetoothService {
            if(instance == null) {
                instance = BluetoothService()
            }

            return instance!!
        }
    }

    init {
        mState = STATE_NONE
        mBluetoothAdapter.name = BLUETOOTH_NAME
    }

    fun setHandler(handler: Handler) {
        mHandler = handler
    }

    fun start() {
        Log.d(TAG, "Start")

        if(mConnectedThread != null) {
            mConnectedThread!!.cancel()
            mConnectedThread = null
        }

        if(mConnectThread != null) {
            mConnectThread!!.cancel()
            mConnectThread = null
        }
    }

    fun connect(device: BluetoothDevice) {
        Log.d(TAG, "connect to $device")

        if(mConnectedThread != null) {
            mConnectedThread!!.cancel()
            mConnectedThread = null
        }

        if(mConnectThread != null) {
            mConnectThread!!.cancel()
            mConnectThread = null
        }

        mConnectThread = ConnectThread(device)
        mConnectThread!!.start()
    }

    fun connected(socket: BluetoothSocket, device: BluetoothDevice) {
        Log.d(TAG, "Connected")

        if(mConnectThread != null) {
            mConnectThread!!.cancel()
            mConnectThread = null
        }

        if (mConnectedThread != null) {
            mConnectedThread!!.cancel()
        }

        mConnectedThread = ConnectedThread(socket)
        mConnectedThread!!.start()

        mHandler!!.obtainMessage(MESSAGE_TOAST, "Connected").sendToTarget()
    }

    fun stop() {
        Log.d(TAG, "stop")

        if (mConnectedThread != null) {
            mConnectedThread!!.cancel()
            mConnectedThread = null
        }

        if (mConnectThread != null) {
            mConnectThread!!.cancel()
            mConnectThread = null
        }

        setState(STATE_NONE)
    }

    fun write(out: ByteArray) {
        // Create temporary object
        var r: ConnectedThread

        // Synchronize a copy of the ConnectedThread
        synchronized(this) {
            if (mState != STATE_CONNECTED) {
                Log.d(TAG, "write: Not connected. mState: $mState")
                mHandler!!.obtainMessage(MESSAGE_TOAST, "Not connected").sendToTarget()
                return
            }
            r = mConnectedThread!!
        }

        r.flush()
        r.write(out)
    }

    fun read(len: Int): ByteArray? {
        var r: ConnectedThread

        synchronized(this) {
            if (mState != STATE_CONNECTED) {
                Log.d(TAG, "write: Not connected. mState: $mState")
                mHandler!!.obtainMessage(MESSAGE_TOAST, "Not connected").sendToTarget()
                return null
            }
            r = mConnectedThread!!
        }

        return r.read(len)
    }

    fun inWaiting(): Int {
        return  if(mConnectedThread == null) 0
                else mConnectedThread!!.inWaiting()
    }

    fun flush() {
        mConnectedThread!!.flush()
    }

    private fun connectionFailed() {
        Log.d(TAG, "Connection Failed. mState: $mState")
        setState(STATE_NONE)
    }


    @Synchronized
    fun getState(): Int {
        return mState
    }

    @Synchronized
    fun setState(state: Int) {
        Log.d(TAG, "Change state $mState to $state")
        mState = state
    }

    inner class ConnectThread(device: BluetoothDevice): Thread() {
        private var mmSocket: BluetoothSocket? = null
        private var mmDevice: BluetoothDevice? = null

        init {
            mmDevice = device
            var tmp: BluetoothSocket? = null

            try {
                tmp = device.createInsecureRfcommSocketToServiceRecord(BLUETOOTH_UUID)
            }
            catch (e: IOException) {
                Log.e(TAG, "Connect create() failed")
            }

            mmSocket = tmp
            setState(STATE_CONNECTING)
        }

        override fun run() {
            Log.i(TAG, "BEGIN mConnectThread")

            mBluetoothAdapter.cancelDiscovery()

            try {
                mmSocket?.connect()
            }
            catch (e: IOException) {
                Log.e(TAG, "Unexpected error occurred while connecting. Error: $e")
                try {
                    mmSocket?.close()
                }
                catch (e2: IOException) {
                    Log.e(TAG, "unable to close socket during connection failure", e2)
                }
                connectionFailed()
                return
            }

            connected(mmSocket!!, mmDevice!!)
        }

        fun cancel() {
            try {
                //mmSocket?.close()
            }
            catch (e: IOException) {
                Log.e(TAG, "close() of connect socket failed", e)
            }
        }
    }

    inner class ConnectedThread(socket: BluetoothSocket) : Thread() {

        private val bufferSize = 2049

        private val mmSocket: BluetoothSocket
        private val mmInStream: InputStream?
        private val mmOutStream: OutputStream?

        private var buffer = ByteArray(bufferSize)
        private var startIndex: Int = 0
        private var endIndex: Int = 0

        init {
            Log.d(TAG, "Create ConnectedThread")

            mmSocket = socket
            var tmpIn: InputStream? = null
            var tmpOut: OutputStream? = null

            // Get the BluetoothSocket input and output streams
            try {
                tmpIn = socket.inputStream
                tmpOut = socket.outputStream
            }
            catch (e: IOException) {
                Log.e(TAG, "temp sockets not created", e)
            }

            mmInStream = tmpIn
            mmOutStream = tmpOut
            setState(STATE_CONNECTED)
            Log.d(TAG, "mState: $mState")
        }

        override fun run() {
            Log.d(TAG, "Begin ConnectedThread")

            var bytes: Int
            flush()

            // Keep listening to the InputStream while connected
            while (mState == STATE_CONNECTED) {
                try {
                    bytes = mmInStream!!.read(buffer, endIndex, (bufferSize - endIndex).coerceAtMost(mmInStream.available()))
                    endIndex += bytes
                }
                catch (e: IOException) {
                    Log.e(TAG, "ConnectedThread: disconnected", e)
                    connectionFailed()
                    break
                }
            }
        }

        fun read(len: Int): ByteArray? {

            if(len > bufferSize) return null

            val timeout: Long = System.currentTimeMillis() + len + 1000

            try {
                while(endIndex - startIndex < len) {
                    if(System.currentTimeMillis() > timeout) {
                        Log.w(TAG, "Read timeout. Len: $len StartIndex: $startIndex EndIndex: $endIndex")
                        return null
                    }
                }

                val tmp = startIndex
                startIndex += len

                if(startIndex >= bufferSize - 1) {
                    flush()
                }

                return buffer.copyOfRange(tmp, tmp + len)
            }
            catch (e: Exception) {
                Log.w(TAG, "Read failed. Error: ${e.message}")
                return null
            }
        }

        /**
         * Write to the connected OutStream.
         *
         * @param buffer The bytes to write
         */
        fun write(buffer: ByteArray) {
            try {
                mmOutStream!!.write(buffer)

                // Share the sent message back to the UI Activity
                mHandler!!.obtainMessage(MESSAGE_WRITE, -1, -1, buffer).sendToTarget()
                Log.d(TAG, "Send Message : ${buffer.toString(Charsets.UTF_8)}")

            }
            catch (e: IOException) {
                Log.e(TAG, "Exception during write", e)
                connectionFailed()
            }
        }

        fun flush() {
            startIndex = 0
            endIndex = 0
        }

        fun inWaiting(): Int {
            return endIndex - startIndex
        }

        fun cancel() {
            try {
                mmSocket.close()
            }
            catch (e: IOException) {
                Log.e(TAG, "ConnectedThread close() failed", e)
            }
        }
    }
}