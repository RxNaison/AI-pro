package com.rx.aipro.presentation.data

import android.content.Context
import androidx.room.*
import com.google.ai.client.generativeai.type.Content
import com.google.ai.client.generativeai.type.TextPart
import com.google.ai.client.generativeai.type.content
import com.rx.aipro.presentation.components.Author
import java.util.UUID

@Entity(tableName = "chat_sessions")
data class ChatSessionEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val modelName: String,
    var firstMessageSnippet: String,
    var lastMessageTimestamp: Long,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "chat_messages",
    foreignKeys = [ForeignKey(
        entity = ChatSessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["sessionId"])]
)
data class ChatMessageEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val text: String,
    @ColumnInfo(defaultValue = "USER")
    val author: String,
    val timestamp: Long,
    val orderInChat: Int
)

@Dao
interface ChatSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: ChatSessionEntity)

    @Update
    suspend fun update(session: ChatSessionEntity)

    @Query("SELECT * FROM chat_sessions ORDER BY lastMessageTimestamp DESC")
    fun getAllSessions(): kotlinx.coroutines.flow.Flow<List<ChatSessionEntity>>

    @Query("SELECT * FROM chat_sessions WHERE id = :sessionId")
    suspend fun getSessionById(sessionId: String): ChatSessionEntity?

    @Query("DELETE FROM chat_sessions WHERE id = :sessionId")
    suspend fun deleteSessionById(sessionId: String)
}

@Dao
interface ChatMessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: ChatMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<ChatMessageEntity>)

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY orderInChat ASC")
    suspend fun getMessagesForSession(sessionId: String): List<ChatMessageEntity>

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun deleteMessagesForSession(sessionId: String)

    @Query("SELECT MAX(orderInChat) FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun fungetLastOrderInChat(sessionId: String): Int?
}

@Database(entities = [ChatSessionEntity::class, ChatMessageEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatSessionDao(): ChatSessionDao
    abstract fun chatMessageDao(): ChatMessageDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "gemini_chat_db"
                )
                    .fallbackToDestructiveMigration(false)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

fun ChatMessageEntity.toChatMessage(): com.rx.aipro.presentation.components.ChatMessage {
    return com.rx.aipro.presentation.components.ChatMessage(
        id = this.id,
        text = this.text,
        author = try { Author.valueOf(this.author) } catch (e: IllegalArgumentException) { Author.MODEL },
        timestamp = this.timestamp
    )
}

fun com.rx.aipro.presentation.components.ChatMessage.toEntity(sessionId: String, order: Int): ChatMessageEntity {
    return ChatMessageEntity(
        id = this.id,
        sessionId = sessionId,
        text = this.text,
        author = this.author.name,
        timestamp = this.timestamp,
        orderInChat = order
    )
}

fun ChatMessageEntity.toContent(): Content {
    return content(role = if (Author.valueOf(this.author) == Author.USER) "user" else "model") {
        text(this@toContent.text)
    }
}

fun Content.toChatMessageEntity(sessionId: String, order: Int, messageId: String = UUID.randomUUID().toString()): ChatMessageEntity? {
    val part = this.parts.firstOrNull()
    val text = (part as? TextPart)?.text
    val author = if (this.role == "user") Author.USER else Author.MODEL
    return text?.let {
        ChatMessageEntity(
            id = messageId,
            sessionId = sessionId,
            text = it,
            author = author.name,
            timestamp = System.currentTimeMillis(),
            orderInChat = order
        )
    }
}