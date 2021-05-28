package com.example.maskdetection

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.BaseAdapter
import android.widget.TextView

class ListAdapter(context: Context, private val dataSource: ArrayList<ListItem>): BaseAdapter() {

    private val inflater: LayoutInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

    override fun getCount(): Int {
        return dataSource.size
    }

    override fun getItem(position: Int): Any {
        return dataSource[position]
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val rowView = inflater.inflate(R.layout.device_info, parent, false)

        val nameTextView = rowView.findViewById<TextView>(R.id.device_name)
        val addrTextView = rowView.findViewById<TextView>(R.id.device_addr)

        nameTextView.text = dataSource[position].name
        addrTextView.text = dataSource[position].address

        return rowView
    }
}