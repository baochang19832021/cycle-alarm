package com.cyclealarm.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class AlarmListActivity : AppCompatActivity() {

    companion object {
        const val ACTION_VIEW = "com.cyclealarm.app.ACTION_VIEW"
        const val EXTRA_TYPE  = "alarm_type"
        const val TYPE_INTERVAL = "interval"
        const val TYPE_HOURLY   = "hourly"
        const val TYPE_MONTHLY  = "monthly"
        const val TYPE_LUNAR    = "lunar"
        const val TYPE_HOLIDAY  = "holiday"
        const val TYPE_LOCATION = "location"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alarm_list)

        val rvFunctionGrid = findViewById<RecyclerView>(R.id.rvFunctionGrid)
        rvFunctionGrid.layoutManager = LinearLayoutManager(this)
        loadFunctionGrid()
    }

    private fun loadFunctionGrid() {
        val rvFunctionGrid = findViewById<RecyclerView>(R.id.rvFunctionGrid)

        val functions = listOf(
            FunctionItem("每N天闹钟", true),
            FunctionItem("每N小时闹钟", false),
            FunctionItem("每月X号闹钟", false),
            FunctionItem("农历日期闹钟", false),
            FunctionItem("法定假日调休闹钟", false),
            FunctionItem("地点触发闹钟", false)
        )

        rvFunctionGrid.adapter = FunctionAdapter(functions) { item ->
            when (item.name) {
                "每N天闹钟" -> {
                    val intent = Intent(this, MainActivity::class.java).apply {
                        action = ACTION_VIEW
                        putExtra(EXTRA_TYPE, TYPE_INTERVAL)
                    }
                    startActivity(intent)
                }
                else -> {
                    Toast.makeText(this, "「${item.name}」开发中，敬请期待", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

data class FunctionItem(
    val name: String,
    val isImplemented: Boolean
)

class FunctionAdapter(
    private var items: List<FunctionItem>,
    private val onClick: (FunctionItem) -> Unit
) : RecyclerView.Adapter<FunctionAdapter.FunctionViewHolder>() {

    inner class FunctionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tvFunctionName)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): FunctionViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_function_card, parent, false)
        return FunctionViewHolder(view)
    }

    override fun onBindViewHolder(holder: FunctionViewHolder, position: Int) {
        val item = items[position]
        holder.tvName.text = item.name
        holder.tvName.setTextColor(if (item.isImplemented) 0xFF1A1A1A.toInt() else 0xFF999999.toInt())
        holder.itemView.setOnClickListener { onClick(item) }
    }

    override fun getItemCount(): Int = items.size
}
