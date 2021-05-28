package com.example.maskdetection

import android.graphics.BitmapFactory
import android.os.*
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream
import kotlin.experimental.*

class ImageViewActivity: AppCompatActivity() {

    companion object {
        private const val TAG = "AJW"
    }

    private lateinit var mBluetoothService: BluetoothService

    private lateinit var tvProgress: TextView
    private lateinit var imageView: ImageView

    private var imageThread: Thread? = null

    @ExperimentalUnsignedTypes
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.image_view)

        mBluetoothService = BluetoothService.getInstance()

        tvProgress = findViewById(R.id.tvProgress)
        imageView = findViewById(R.id.imageView)

        MainActivity.onBackground = false

        getSelectedImage(intent.getStringExtra("path")!!)
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "ImageViewActivity.onResume()")
        MainActivity.onBackground = false
    }

    @ExperimentalUnsignedTypes
    private fun getSelectedImage(name: String) {
        if(mBluetoothService.getState() != 2) {
            Toast.makeText(this, "Please connect to device.", Toast.LENGTH_SHORT).show()
            return
        }

        imageThread = Thread {
            try {
                var data: ByteArray?
                var length: UInt
                var failure = 0

                while(true) {
                    try {
                        mBluetoothService.write("1".toByteArray(Charsets.UTF_8))
                        Thread.sleep(1000)
                        mBluetoothService.write(name.toByteArray(Charsets.UTF_8))

                        //Read 4Bytes of length data and convert to Int
                        data = mBluetoothService.read(4)
                        length = ((data!![0].toUInt() and 0xFFu).shl(24)
                                or (data[1].toUInt() and 0xFFu).shl(16)
                                or (data[2].toUInt() and 0xFFu).shl(8)
                                or (data[3].toUInt() and 0xFFu).shl(0))

                        Log.d(TAG, "Image length: $length data: " +
                                "${(data[0].toUInt() and 0xFFu)}, " +
                                "${(data[1].toUInt() and 0xFFu)}, " +
                                "${(data[2].toUInt() and 0xFFu)}, " +
                                "${(data[3].toUInt() and 0xFFu)}")

                        if(102400u < length && length < 409600u) {
                            mBluetoothService.write(byteArrayOf(255.toByte()))
                            break
                        }
                        else {
                            throw Exception("Value out of range")
                        }
                    }
                    catch (i: InterruptedException) {
                        throw i
                    }
                    catch (e: Exception) {

                        Log.w(TAG, "Error occurred while reading image fragment. Error: ${e.message}")

                        failure++
                        if(failure == 3) {
                            finish()
                        }

                        continue
                    }
                }

                mHandler.obtainMessage(MESSAGE_TOAST, "Image length: $length").sendToTarget()

                val file = File("$filesDir/$name")
                val fileOutputStream = FileOutputStream(file)
                Log.d(TAG, "Save file at ${file.absolutePath}")

                var bytesToRead: Int = length.toInt()
                var fragment = 0
                var checksum: Byte = 0
                val fragmentNum = (bytesToRead - 1) / 2048 + 1

                while(true) {
                    try {

                        Log.d(TAG, "Try to read fragment $fragment. BytesToRead: $bytesToRead failure: $failure")

                        mBluetoothService.write(byteArrayOf(fragment.toByte()))

                        data = mBluetoothService.read(2048.coerceAtMost(bytesToRead))
                        checksum = mBluetoothService.read(1)!![0]

                        Log.d(TAG, "Checksum : ${checksum.toUByte()}")

                        for(i in data!!) {
                            checksum = checksum xor i
                        }

                        Log.d(TAG, "Checksum : ${checksum.toUByte()}")

                        if(checksum != 0.toByte()) {
                            Log.d(TAG, "Checksum not matched. Fragment: $fragment")
                            continue
                        }

                        fileOutputStream.write(data)
                        fileOutputStream.flush()

                        if(bytesToRead <= 2048) {
                            fileOutputStream.close()
                            mBluetoothService.write(byteArrayOf(255.toByte()))
                            mHandler.obtainMessage(MESSAGE_UPDATE_IMAGE_COMPLETE, file.absolutePath).sendToTarget()
                            break
                        }
                    }
                    catch (i: InterruptedException) {
                        throw i
                    }
                    catch (e: Exception) {

                        Log.w(TAG, "Error occurred while reading image fragment. Error: ${e.message}")

                        failure++
                        if(failure == 3) {
                            fileOutputStream.close()
                            finish()
                        }

                        continue
                    }

                    fragment++
                    bytesToRead -= 2048
                    mHandler.obtainMessage(MESSAGE_UPDATE_IMAGE_FRAGMENT, ((fragment + 1) * 100) / fragmentNum).sendToTarget()
                }
            }
            catch (e: InterruptedException) {
                Log.d(TAG, "Load image thread interrupted")
            }
            catch (e: Exception) {

            }
        }

        imageThread!!.start()
    }

    private fun showImage(path: String) {

        try {
            val image = BitmapFactory.decodeFile(path)
            imageView.setImageBitmap(image)
            tvProgress.visibility = View.GONE
            imageView.visibility = View.VISIBLE
        }
        catch (e: Exception) {
            Log.w(TAG, "Open image failed. Error: ${e.message}")
        }
    }

    private var mHandler: Handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)

            when(msg.what) {
                MESSAGE_UPDATE_IMAGE_FRAGMENT -> {
                    val str = (msg.obj as Int).toString() + "%"
                    tvProgress.text = str
                }

                MESSAGE_UPDATE_IMAGE_COMPLETE -> {
                    tvProgress.text = "100%"
                    showImage(msg.obj as String)
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "ImageViewActivity.onPause()")
        MainActivity.onBackground = true
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "ImageViewActivity.onStop()")
        imageThread?.interrupt()
        //MainActivity.onBackground = true
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "ImageViewActivity.onStop()")
        imageThread?.interrupt()
        //MainActivity.onBackground = true
    }
}