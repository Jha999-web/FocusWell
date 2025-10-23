package com.example.focuswell

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import com.example.focuswell.data.Task
import com.example.focuswell.data.TaskDatabase
import com.example.focuswell.utils.AlarmReceiver
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var taskEditText: EditText
    private lateinit var categorySpinner: Spinner
    private lateinit var timePickerButton: Button
    private lateinit var addButton: Button
    private lateinit var tasksListView: ListView

    private lateinit var database: TaskDatabase
    private var reminderTimeInMillis: Long = 0

    private val categories = listOf("Study", "Health")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        taskEditText = findViewById(R.id.taskEditText)
        categorySpinner = findViewById(R.id.categorySpinner)
        timePickerButton = findViewById(R.id.timePickerButton)
        addButton = findViewById(R.id.addButton)
        tasksListView = findViewById(R.id.tasksListView)

        // Setup category spinner
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categories)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        categorySpinner.adapter = adapter

        // Setup database
        database = Room.databaseBuilder(
            applicationContext,
            TaskDatabase::class.java,
            "task_database"
        ).build()

        // Pick reminder time
        timePickerButton.setOnClickListener {
            showTimePicker()
        }

        // Add task button
        addButton.setOnClickListener {
            val taskText = taskEditText.text.toString().trim()
            val category = categorySpinner.selectedItem.toString()

            if (taskText.isEmpty()) {
                Toast.makeText(this, "Please enter a task", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (reminderTimeInMillis == 0L) {
                Toast.makeText(this, "Please pick a reminder time", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            addTask(taskText, category, reminderTimeInMillis)
        }

        loadTasks()
    }

    private fun showTimePicker() {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)

        val timePicker = android.app.TimePickerDialog(
            this,
            { _, selectedHour, selectedMinute ->
                val now = Calendar.getInstance()
                now.set(Calendar.HOUR_OF_DAY, selectedHour)
                now.set(Calendar.MINUTE, selectedMinute)
                now.set(Calendar.SECOND, 0)
                now.set(Calendar.MILLISECOND, 0)

                if (now.timeInMillis <= System.currentTimeMillis()) {
                    // If selected time is before now, add one day
                    now.add(Calendar.DAY_OF_YEAR, 1)
                }

                reminderTimeInMillis = now.timeInMillis
                timePickerButton.text = String.format("%02d:%02d", selectedHour, selectedMinute)
            },
            hour,
            minute,
            true
        )
        timePicker.show()
    }

    private fun addTask(text: String, category: String, reminderTime: Long) {
        val newTask = Task(text = text, category = category, reminderTime = reminderTime)

        lifecycleScope.launch {
            database.taskDao().insertTask(newTask)

            scheduleReminder(newTask)

            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "Task added", Toast.LENGTH_SHORT).show()
                taskEditText.text.clear()
                reminderTimeInMillis = 0
                timePickerButton.text = "Pick Reminder Time"
                loadTasks()
            }
        }
    }

    private fun loadTasks() {
        lifecycleScope.launch {
            val allTasks = database.taskDao().getTasksByCategory(categories[categorySpinner.selectedItemPosition])

            withContext(Dispatchers.Main) {
                val taskTexts = allTasks.map {
                    val date = Date(it.reminderTime)
                    "${it.text} - ${date.hours.toString().padStart(2, '0')}:${date.minutes.toString().padStart(2, '0')}"
                }

                val adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_list_item_1, taskTexts)
                tasksListView.adapter = adapter
            }
        }
    }

    private fun scheduleReminder(task: Task) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(this, AlarmReceiver::class.java).apply {
            putExtra("taskText", task.text)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            this,
            task.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setExact(
            AlarmManager.RTC_WAKEUP,
            task.reminderTime,
            pendingIntent
        )
    }
}
