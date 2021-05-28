package com.example.maskdetection

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Debug
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import com.example.maskdetection.ListAdapter
import com.example.maskdetection.ListItem

class DeviceScanActivity: Activity() {
    
    val TAG = "AJW"

    var mBtAdapter: BluetoothAdapter? = null

    var pairedDeviceList: ListView? = null
    var scannedDeviceList: ListView? = null

    var pairedDeviceAdapter: ListAdapter? = null
    var scannedDeviceAdapter: ListAdapter? = null

    var pairedList = ArrayList<ListItem>()
    var scannedList = ArrayList<ListItem>()

    var rescan: Button? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.device_list)
        setResult(Activity.RESULT_CANCELED)

        Log.d(TAG, "DeviceScanActivity")

        pairedDeviceList = findViewById(R.id.pairedDeviceList)
        scannedDeviceList = findViewById(R.id.scannedDeviceList)

        pairedDeviceAdapter = ListAdapter(this, pairedList)
        scannedDeviceAdapter = ListAdapter(this, scannedList)

        pairedDeviceList?.adapter = pairedDeviceAdapter
        scannedDeviceList?.adapter = scannedDeviceAdapter

        rescan = findViewById(R.id.btnRescan)

        mBtAdapter = BluetoothAdapter.getDefaultAdapter()
        
        var filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        registerReceiver(mReceiver, filter)

        filter = IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
        registerReceiver(mReceiver, filter)

        filter = IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        registerReceiver(mReceiver, filter)

        findViewById<Button>(R.id.btnRescan).setOnClickListener {
            mBtAdapter?.startDiscovery()
        }

        pairedDeviceList?.onItemClickListener = onItemClickListener
        scannedDeviceList?.onItemClickListener = onItemClickListener

        pairedList.clear()

        var pairedDevices = mBtAdapter?.bondedDevices
        if(pairedDevices?.size!! > 0)
        {
            for(device in pairedDevices)
            {
                pairedList.add(ListItem(device.name, device.address))
            }
        }

        pairedDeviceAdapter?.notifyDataSetChanged()
    }

    private val onItemClickListener = AdapterView.OnItemClickListener {
        _: AdapterView<*>, v: View, _: Int, _: Long ->

        val addr: String = v.findViewById<TextView>(R.id.device_addr).text.toString()

        var intent = Intent()
        intent.putExtra("device_address", addr)

        Log.d(TAG, "Selected device address: $addr")

        setResult(Activity.RESULT_OK, intent)
        finish()
    }


    private val mReceiver = object: BroadcastReceiver() {

        override fun onReceive(context: Context?, intent: Intent?) {
            when(intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = intent?.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    val deviceName = if(device?.name != null) device.name else "null"
                    val deviceAddr = if(device?.address != null) device.address else "null"

                    Log.d(TAG, "Name: $deviceName Addr: $deviceAddr")
                    scannedList.add(ListItem(deviceName, deviceAddr))
                    scannedDeviceAdapter?.notifyDataSetChanged()
                }

                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    Log.d(TAG, "Discovery Start")

                    if(mBtAdapter?.scanMode != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
                        var discoverable = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                        discoverable.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
                        startActivity(discoverable)
                    }

                    scannedList.clear()
                    scannedDeviceAdapter?.notifyDataSetChanged()
                }

                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    Log.d(TAG, "Discovery Finish")
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mBtAdapter?.cancelDiscovery()
        unregisterReceiver(mReceiver)
    }
}