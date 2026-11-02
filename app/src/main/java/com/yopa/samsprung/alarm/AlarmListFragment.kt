package com.yopa.samsprung.alarm

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yopa.samsprung.R

class AlarmListFragment : Fragment() {

    private lateinit var rvAlarms: RecyclerView
    private lateinit var layoutEmpty: LinearLayout
    private lateinit var btnEmptyOpenClock: View
    private var adapter: AlarmAdapter? = null

    private val alarmChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            loadAlarms()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_alarm_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rvAlarms = view.findViewById(R.id.rv_alarm_list)
        layoutEmpty = view.findViewById(R.id.layout_alarm_empty)
        btnEmptyOpenClock = view.findViewById(R.id.btn_empty_open_clock)

        rvAlarms.layoutManager = LinearLayoutManager(requireContext())
        adapter = AlarmAdapter(emptyList()) { item ->
            AlarmRepository.openClockApp(requireContext(), item.showIntent)
        }
        rvAlarms.adapter = adapter

        btnEmptyOpenClock.setOnClickListener {
            AlarmRepository.openClockApp(requireContext(), null)
        }

        val filter = IntentFilter().apply {
            addAction(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED)
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        @Suppress("UnspecifiedRegisterReceiverFlag")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(alarmChangedReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            requireContext().registerReceiver(alarmChangedReceiver, filter)
        }
    }

    override fun onResume() {
        super.onResume()
        loadAlarms()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        try {
            requireContext().unregisterReceiver(alarmChangedReceiver)
        } catch (_: Exception) {}
    }

    private fun loadAlarms() {
        val ctx = context ?: return
        val items = AlarmRepository.getUpcomingAlarms(ctx)
        if (items.isEmpty()) {
            layoutEmpty.visibility = View.VISIBLE
            rvAlarms.visibility = View.GONE
        } else {
            layoutEmpty.visibility = View.GONE
            rvAlarms.visibility = View.VISIBLE
            adapter?.updateItems(items)
        }
    }
}
