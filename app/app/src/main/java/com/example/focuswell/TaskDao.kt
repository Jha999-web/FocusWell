package com.example.focuswell.data

import androidx.room.*

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE category = :category")
    suspend fun getTasksByCategory(category: String): List<Task>

    @Insert
    suspend fun insertTask(task: Task): Long

    @Delete
    suspend fun deleteTask(task: Task)
}
