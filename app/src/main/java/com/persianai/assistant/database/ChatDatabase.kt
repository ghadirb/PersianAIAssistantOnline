package com.persianai.assistant.database

import android.content.Context
import com.persianai.assistant.models.ChatMessage
import com.persianai.assistant.models.MessageRole

/**
 * پایگاه داده Room برای ذخیره تاریخچه چت
 * TEMPORARILY DISABLED - Will be re-enabled with KSP in v1.1
 */

/*
@Database(entities = [ChatMessageEntity::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class ChatDatabase : RoomDatabase() {
    
    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile
        private var INSTANCE: ChatDatabase? = null

        fun getDatabase(context: Context): ChatDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ChatDatabase::class.java,
                    "chat_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

@Entity(tableName = "messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @ColumnInfo(name = "role")
    val role: MessageRole,
    
    @ColumnInfo(name = "content")
    val content: String,
    
    @ColumnInfo(name = "timestamp")
    val timestamp: Long,
    
    @ColumnInfo(name = "audio_path")
    val audioPath: String? = null,
    
    @ColumnInfo(name = "is_error")
    val isError: Boolean = false
)

@Dao
interface ChatDao {
    
    @Query("SELECT * FROM messages ORDER BY timestamp ASC")
    suspend fun getAllMessages(): List<ChatMessage>
    
    @Insert
    suspend fun insertMessage(message: ChatMessage)
    
    @Query("DELETE FROM messages")
    suspend fun deleteAllMessages()
    
    @Query("DELETE FROM messages WHERE timestamp < :beforeTimestamp")
    suspend fun deleteOldMessages(beforeTimestamp: Long)
    
    @Query("SELECT COUNT(*) FROM messages")
    suspend fun getMessageCount(): Int
}

class Converters {
    @TypeConverter
    fun fromMessageRole(role: MessageRole): String {
        return role.name
    }

    @TypeConverter
    fun toMessageRole(value: String): MessageRole {
        return MessageRole.valueOf(value)
    }
}
*/
