package com.example.maskdetection

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.*
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.util.*
import kotlin.collections.ArrayList

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "AJW"

        private const val PERMISSIONS_REQUEST_CODE = 100

        private const val REQUEST_ENABLE_BT = 1000
        private const val REQUEST_BLUETOOTH_DEVICE = 1001

        val REQUIRED_PERMISSIONS = arrayOf(
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                android.Manifest.permission.BLUETOOTH,
                android.Manifest.permission.BLUETOOTH_ADMIN,
                android.Manifest.permission.ACCESS_COARSE_LOCATION,
                android.Manifest.permission.ACCESS_FINE_LOCATION,
        )

        public var onBackground: Boolean = false
    }

    private var mBluetoothAdapter: BluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    private lateinit var mBluetoothService: BluetoothService

    private var listImage: ListView? = null
    private var arrayAdapter: ArrayAdapter<String>? = null

    private var notifyThread: Thread? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        mBluetoothService = BluetoothService.getInstance()
        mBluetoothService.setHandler(mHandler)

        checkPermission()
        setupBluetoothService()
        createNotificationChannel()

        findViewById<Button>(R.id.btnScan).setOnClickListener {
            //Toast.makeText(this, "onClick", Toast.LENGTH_SHORT).show()

            val intent = Intent(this, DeviceScanActivity::class.java)
            startActivityForResult(intent, REQUEST_BLUETOOTH_DEVICE)
        }

        findViewById<Button>(R.id.btnRefresh).setOnClickListener {
           // Toast.makeText(this, "onClick", Toast.LENGTH_SHORT).show()

            refreshDetectedList()
        }

        listImage = findViewById(R.id.listImage)

        try {
            arrayAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, ArrayList<String>())
            listImage?.adapter = arrayAdapter
        }
        catch (e: Exception) {
            Log.w(TAG, "Error occurred. Error: ${e.message}")
        }

        listImage?.setOnItemClickListener { _, _, position, _ ->

            if(mBluetoothService.getState() != 2) {
                Toast.makeText(this, "Please connect to device.", Toast.LENGTH_SHORT).show()
                return@setOnItemClickListener
            }

            val intent = Intent(this, ImageViewActivity::class.java)
            intent.putExtra("path", arrayAdapter?.getItem(position))

            Log.d(TAG, "Before activity")
            startActivity(intent)
            Log.d(TAG, "After activity")
        }

    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume()")
        onBackground = false
    }

    private fun checkPermission() {
        if(ActivityCompat.checkSelfPermission(applicationContext, android.Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Permission BLUETOOTH denied")
        }

        if(ActivityCompat.checkSelfPermission(applicationContext, android.Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Permission BLUETOOTH_ADMIN denied")
        }

        if(ActivityCompat.checkSelfPermission(applicationContext, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Permission ACCESS_FINE_LOCATION denied")
        }

        if(ActivityCompat.checkSelfPermission(applicationContext, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Permission ACCESS_COARSE_LOCATION denied")
        }

        ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, PERMISSIONS_REQUEST_CODE)
    }

    private fun setupBluetoothService() {
        if(!mBluetoothAdapter.isEnabled)
        {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
        }
    }

    private fun refreshDetectedList() {
        if(mBluetoothService.getState() != 2) {
            Toast.makeText(this, "Please connect to device.", Toast.LENGTH_SHORT).show()
            return
        }

        Thread {

            mBluetoothService.write("2".toByteArray(Charsets.UTF_8))

            try {
                var data: ByteArray? = mBluetoothService.read(1)
                val length: Byte = data!![0]

                data = mBluetoothService.read(length * 24)

                val imageList = data!!.decodeToString().split('\n').dropLast(1)
                Collections.sort(imageList, Collections.reverseOrder())

                mHandler.obtainMessage(MESSAGE_UPDATE_IMAGE_LIST, imageList).sendToTarget()

            } catch (e: Exception) {
                Log.w(TAG, "RefreshDetectedList failed. Error: ${e.message}")
            }

        }.start()
    }

    private fun createNotificationChannel() {

        val builder = NotificationCompat.Builder(this, "MaskDetection")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Without mask detected")
                .setContentText("XX-XX-XX_xx-xx-xx")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Mask Detection"
            val descriptionText = "mask detection"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel("MaskDetection", name, importance).apply {
                description = descriptionText
            }
            // Register the channel with the system
            val notificationManager: NotificationManager =
                    getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        notifyThread = Thread {
            var data: ByteArray?
            while(true) {
                try {
                    if (mBluetoothService.getState() == 2 && onBackground) {

                        mBluetoothService.write(byteArrayOf(51))
                        data = mBluetoothService.read(1)

                        if (data!![0].toInt() == 1) {
                            //if (mBluetoothService.inWaiting() >= 24) {

                            Log.d(TAG, "Detected")

                            val path = mBluetoothService.read(23)!!.toString(Charsets.UTF_8)

                            if (path.endsWith(".png")) {

                                val intent = Intent(this, ImageViewActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                    putExtra("path", path)
                                }

                                val pendingIntent = PendingIntent.getActivity(this, (Math.random() * 1000).toInt(), intent, PendingIntent.FLAG_UPDATE_CURRENT)

                                builder.setContentText(path)
                                builder.setContentIntent(pendingIntent)

                                with(NotificationManagerCompat.from(this)) {
                                    notify(1234, builder.build())
                                }
                            } else {
                                Log.d(TAG, "Path doesn't ends with .png")
                            }
                        }
                    }

                    Thread.sleep(3000)
                }
                catch (i: InterruptedException) {
                    break
                }
                catch (e: Exception) {
                    Log.d(TAG, "Notify error. Error: ${e.message}")
                }
            }
        }

        notifyThread!!.start()
    }

    private val mHandler: Handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                MESSAGE_TOAST -> {
                    Toast.makeText(applicationContext, msg.obj as String, Toast.LENGTH_SHORT).show()
                }

                MESSAGE_UPDATE_IMAGE_LIST -> {
                    arrayAdapter?.clear()
                    arrayAdapter?.addAll(msg.obj as List<String>)
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when(requestCode)
        {
            REQUEST_BLUETOOTH_DEVICE -> {
                if(resultCode == RESULT_OK) {

                    val mac = data?.getStringExtra("device_address")
                    Log.d(TAG, "Selected device addr : $mac")

                    val device: BluetoothDevice? = mBluetoothAdapter.getRemoteDevice(mac)
                    if (device != null) {
                        Log.d(TAG, "connectDevice")
                        mBluetoothService.connect(device)
                    }
                    else {
                        Log.d(TAG, "connectDevice: device is null")
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        onBackground = true
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "onStop()")
        onBackground = true
    }

    override fun onDestroy() {
        super.onDestroy()
        notifyThread?.interrupt()
    }
}