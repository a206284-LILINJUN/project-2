package com.example.happybirthday

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.annotation.SuppressLint
import android.app.Application
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.room.*
import com.example.happybirthday.ui.theme.HappyBirthdayTheme
import com.google.android.gms.location.LocationServices
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query as ApiQuery
import java.text.SimpleDateFormat
import java.util.*

// ══════════════════════════════════════════════════════════════════════════════
// PROJECT 2 COMPLETE VERSION
// Same software: CommuniLink, SDG 11 Sustainable Cities & Communities
//
// ✅ Minimum 7 screens:
//    1 Chats, 2 Chat Detail, 3 Me, 4 Edit Profile, 5 Local Board,
//    6 Voice Calendar, 7 Air Quality GPS + API, 8 Cloud Chat Firebase
//
// ✅ Room Database: messages, notices, profile
// ✅ Firebase Firestore: cloud chat records + online community notices
// ✅ Retrofit REST API: Open-Meteo Air Quality API
// ✅ Hardware Sensor: GPS / Location sensor
// ══════════════════════════════════════════════════════════════════════════════

// ══════════════════════════════════════════════════════════════════════════════
// LAYER 1 ── ROOM ENTITIES
// ══════════════════════════════════════════════════════════════════════════════

/** Stores every chat message sent/received in the app. */
@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val conversation: String = "General",
    val sender: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis()
)

/** Stores community-board notices posted by residents. */
@Entity(tableName = "notices")
data class NoticeEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val author: String,
    val title: String,
    val content: String
)

/** Stores calendar events with voice integration. */
@Entity(tableName = "calendar_events")
data class CalendarEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val dateTime: Long,
    val type: String = "Reminder"
)

/** Stores the single user profile. */
@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: Int = 1,
    val name: String = "LI LINJUN",
    val bio: String = "Community Member",
    val phone: String = "123-456789"
)

// ══════════════════════════════════════════════════════════════════════════════
// LAYER 2 ── DAO INTERFACES
// ══════════════════════════════════════════════════════════════════════════════

@Dao
interface MessageDao {
    @Insert
    suspend fun insert(message: MessageEntity)

    @Query("SELECT * FROM messages ORDER BY conversation ASC, createdAt ASC")
    fun getAll(): Flow<List<MessageEntity>>

    @Delete
    suspend fun delete(message: MessageEntity)
}

@Dao
interface NoticeDao {
    @Insert
    suspend fun insert(notice: NoticeEntity)

    @Query("SELECT * FROM notices ORDER BY id DESC")
    fun getAll(): Flow<List<NoticeEntity>>

    @Delete
    suspend fun delete(notice: NoticeEntity)
}

@Dao
interface CalendarEventDao {
    @Insert
    suspend fun insert(event: CalendarEventEntity)

    @Query("SELECT * FROM calendar_events ORDER BY dateTime ASC")
    fun getAll(): Flow<List<CalendarEventEntity>>

    @Query("DELETE FROM calendar_events WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM calendar_events WHERE title LIKE '%' || :title || '%'")
    suspend fun deleteByTitle(title: String)
}

@Dao
interface ProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: ProfileEntity)

    @Query("SELECT * FROM profile WHERE id = 1")
    fun get(): Flow<ProfileEntity?>

    @Update
    suspend fun update(profile: ProfileEntity)
}

// ══════════════════════════════════════════════════════════════════════════════
// LAYER 3 ── ROOM DATABASE
// ══════════════════════════════════════════════════════════════════════════════

@Database(
    entities = [MessageEntity::class, NoticeEntity::class, ProfileEntity::class, CalendarEventEntity::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun noticeDao(): NoticeDao
    abstract fun profileDao(): ProfileDao
    abstract fun calendarDao(): CalendarEventDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: android.content.Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "communilink.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// RETROFIT WEB API ── OPEN-METEO AIR QUALITY
// ══════════════════════════════════════════════════════════════════════════════

data class AirQualityResponse(
    val latitude: Double?,
    val longitude: Double?,
    val current: CurrentAirQuality?
)

data class CurrentAirQuality(
    val time: String?,
    val pm10: Double?,
    @SerializedName("pm2_5") val pm25: Double?,
    @SerializedName("carbon_monoxide") val carbonMonoxide: Double?,
    @SerializedName("nitrogen_dioxide") val nitrogenDioxide: Double?
)

interface OpenMeteoAirQualityApi {
    @GET("v1/air-quality")
    suspend fun getCurrentAirQuality(
        @ApiQuery("latitude") latitude: Double,
        @ApiQuery("longitude") longitude: Double,
        @ApiQuery("current") current: String = "pm10,pm2_5,carbon_monoxide,nitrogen_dioxide",
        @ApiQuery("timezone") timezone: String = "auto"
    ): AirQualityResponse
}

object ApiClient {
    val airQualityApi: OpenMeteoAirQualityApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://air-quality-api.open-meteo.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OpenMeteoAirQualityApi::class.java)
    }
}

data class AirQualityUiState(
    val isLoading: Boolean = false,
    val message: String = "Press the button to use GPS and load live air-quality data.",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val pm10: Double? = null,
    val pm25: Double? = null,
    val carbonMonoxide: Double? = null,
    val nitrogenDioxide: Double? = null,
    val time: String? = null,
    val usedFallbackLocation: Boolean = false
)

// ══════════════════════════════════════════════════════════════════════════════
// FIREBASE FIRESTORE MODEL
// ══════════════════════════════════════════════════════════════════════════════

data class CloudChatMessage(
    val id: String = "",
    val conversation: String = "",
    val sender: String = "",
    val content: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val deviceId: String = ""
)

data class CloudCommunityNotice(
    val id: String = "",
    val author: String = "",
    val title: String = "",
    val content: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val deviceId: String = ""
)

// ══════════════════════════════════════════════════════════════════════════════
// LAYER 4 ── REPOSITORY
// ══════════════════════════════════════════════════════════════════════════════

class AppRepository(db: AppDatabase) {
    private val messageDao = db.messageDao()
    val allMessages: Flow<List<MessageEntity>> = messageDao.getAll()

    suspend fun insertMessage(conversation: String, sender: String, content: String) {
        messageDao.insert(
            MessageEntity(
                conversation = conversation,
                sender = sender,
                content = content
            )
        )
    }

    suspend fun deleteMessage(message: MessageEntity) {
        messageDao.delete(message)
    }

    private val noticeDao = db.noticeDao()
    val allNotices: Flow<List<NoticeEntity>> = noticeDao.getAll()

    suspend fun insertNotice(author: String, title: String, content: String) {
        noticeDao.insert(NoticeEntity(author = author, title = title, content = content))
    }

    suspend fun deleteNotice(notice: NoticeEntity) {
        noticeDao.delete(notice)
    }

    private val profileDao = db.profileDao()
    val profile: Flow<ProfileEntity?> = profileDao.get()

    suspend fun upsertProfile(name: String, bio: String, phone: String) {
        profileDao.upsert(ProfileEntity(name = name, bio = bio, phone = phone))
    }

    private val calendarDao = db.calendarDao()
    val allEvents: Flow<List<CalendarEventEntity>> = calendarDao.getAll()

    suspend fun insertEvent(title: String, time: Long) {
        calendarDao.insert(CalendarEventEntity(title = title, dateTime = time))
    }

    suspend fun deleteEventById(id: Int) {
        calendarDao.deleteById(id)
    }

    suspend fun deleteEventByTitle(title: String) {
        calendarDao.deleteByTitle(title)
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// LAYER 5 ── VIEWMODEL
// ══════════════════════════════════════════════════════════════════════════════

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val appStartTime = System.currentTimeMillis()
    private val notifiedChatIds = mutableSetOf<String>()
    private val notifiedNoticeIds = mutableSetOf<String>()
    private var firstChatSnapshot = true
    private var lastChatCreatedAt = 0L
    private var firstNoticeSnapshot = true
    private var lastNoticeCreatedAt = 0L

    private val repository: AppRepository = AppRepository(
        AppDatabase.getInstance(application)
    )

    val messages: StateFlow<List<MessageEntity>> = repository.allMessages
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val notices: StateFlow<List<NoticeEntity>> = repository.allNotices
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val profile: StateFlow<ProfileEntity> = repository.profile
        .map { it ?: ProfileEntity() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ProfileEntity()
        )

    val calendarEvents: StateFlow<List<CalendarEventEntity>> = repository.allEvents
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    private val _airQuality = MutableStateFlow(AirQualityUiState())
    val airQuality: StateFlow<AirQualityUiState> = _airQuality

    private val _cloudChatMessages = MutableStateFlow<List<CloudChatMessage>>(emptyList())
    val cloudChatMessages: StateFlow<List<CloudChatMessage>> = _cloudChatMessages

    private val _cloudCommunityNotices = MutableStateFlow<List<CloudCommunityNotice>>(emptyList())
    val cloudCommunityNotices: StateFlow<List<CloudCommunityNotice>> = _cloudCommunityNotices

    private val _cloudMessage = MutableStateFlow("Firestore ready. Chat records will be saved to cloud.")
    val cloudMessage: StateFlow<String> = _cloudMessage

    private val _noticeCloudMessage = MutableStateFlow("Firestore ready. Community notices will be saved to cloud.")
    val noticeCloudMessage: StateFlow<String> = _noticeCloudMessage

    private var cloudChatListener: ListenerRegistration? = null
    private var cloudNoticeListener: ListenerRegistration? = null

    init {
        viewModelScope.launch {
            repository.profile.collect { existing ->
                if (existing == null) {
                    repository.upsertProfile("LI LINJUN", "SDG 11 Community Member", "123-456789")
                    repository.insertMessage("Alice", "Alice", "Hi neighbour!")
                    repository.insertMessage("Bob", "Bob", "Community BBQ this Sunday!")
                    repository.insertMessage("Community Group", "Admin", "Welcome to CommuniLink group chat.")
                    repository.insertNotice("Admin", "Road Closure", "Jalan Utama closed Saturday 9am-5pm.")
                    repository.insertNotice("Alice", "Lost Cat", "Orange tabby missing near Block C.")
                }
                return@collect
            }
        }

        startCloudChatListener()
        startCloudNoticeListener()
    }

    // ── Public actions called from UI ────────────────────────────────────────

    fun sendMessage(conversation: String, text: String) {
        if (text.isBlank()) return
        val cleanText = text.trim()
        val now = System.currentTimeMillis()
        val senderName = profile.value.name.ifBlank { "Me" }

        viewModelScope.launch {
            // 1) Room local persistence: offline backup on this device.
            repository.insertMessage(conversation, senderName, cleanText)

            // 2) Firebase Firestore remote database: another device can read this message.
            val cloudChat = CloudChatMessage(
                conversation = conversation,
                sender = senderName,
                content = cleanText,
                createdAt = now,
                deviceId = currentDeviceId()
            )

            Firebase.firestore.collection("cloud_chat_messages")
                .add(cloudChat)
                .addOnSuccessListener {
                    _cloudMessage.value = "Chat uploaded to Firestore remote database."
                }
                .addOnFailureListener { error ->
                    _cloudMessage.value = "Chat upload failed: ${error.message}"
                }
        }
    }

    fun addNotice(title: String, content: String) {
        if (title.isBlank() || content.isBlank()) return

        val cleanTitle = title.trim()
        val cleanContent = content.trim()
        val authorName = profile.value.name

        viewModelScope.launch {
            // 1) Room local persistence: offline backup.
            repository.insertNotice(authorName, cleanTitle, cleanContent)

            // 2) Firebase Firestore remote database: another device can read this notice.
            val cloudNotice = CloudCommunityNotice(
                author = authorName,
                title = cleanTitle,
                content = cleanContent,
                createdAt = System.currentTimeMillis(),
                deviceId = currentDeviceId()
            )

            Firebase.firestore.collection("community_notices")
                .add(cloudNotice)
                .addOnSuccessListener {
                    _noticeCloudMessage.value = "Notice uploaded to Firestore remote database."
                }
                .addOnFailureListener { error ->
                    _noticeCloudMessage.value = "Notice upload failed: ${error.message}"
                }
        }
    }

    fun updateProfile(name: String, bio: String, phone: String) {
        viewModelScope.launch { repository.upsertProfile(name, bio, phone) }
    }

    fun deleteNotice(notice: NoticeEntity) {
        viewModelScope.launch { repository.deleteNotice(notice) }
    }

    fun deleteMessage(message: MessageEntity) {
        viewModelScope.launch { repository.deleteMessage(message) }
    }

    fun deleteEventById(id: Int) {
        viewModelScope.launch { repository.deleteEventById(id) }
    }

    // ── Voice Command Processing ──────────────────────────────────────────────

    fun processVoiceCommand(command: String, onResult: (String) -> Unit) {
        val lower = command.lowercase()
        viewModelScope.launch {
            when {
                lower.contains("添加") || lower.contains("提醒") || lower.contains("add") -> {
                    val title = command.replace(Regex("添加|提醒|add|remind me to", RegexOption.IGNORE_CASE), "").trim()
                    if (title.isNotEmpty()) {
                        repository.insertEvent(title, System.currentTimeMillis())
                        onResult("已为您添加日程：$title")
                    } else {
                        onResult("请告诉我具体的日程内容")
                    }
                }
                lower.contains("删除") || lower.contains("取消") || lower.contains("delete") -> {
                    val title = command.replace(Regex("删除|取消|delete|remove", RegexOption.IGNORE_CASE), "").trim()
                    if (title.isNotEmpty()) {
                        repository.deleteEventByTitle(title)
                        onResult("已尝试删除包含 \"$title\" 的日程")
                    } else {
                        onResult("请告诉我您想删除哪个日程")
                    }
                }
                lower.contains("查看") || lower.contains("显示") || lower.contains("查询") || lower.contains("show") || lower.contains("list") -> {
                    onResult("正在为您显示所有日程")
                }
                else -> {
                    onResult("抱歉，我没听懂。您可以试着说“添加开会”或“查看日程”")
                }
            }
        }
    }

    // ── Retrofit API request ─────────────────────────────────────────────────

    fun fetchAirQuality(latitude: Double, longitude: Double, usedFallbackLocation: Boolean = false) {
        viewModelScope.launch {
            _airQuality.value = AirQualityUiState(
                isLoading = true,
                message = "Loading live data from Open-Meteo REST API...",
                latitude = latitude,
                longitude = longitude,
                usedFallbackLocation = usedFallbackLocation
            )

            try {
                val result = ApiClient.airQualityApi.getCurrentAirQuality(latitude, longitude)
                val current = result.current

                _airQuality.value = AirQualityUiState(
                    isLoading = false,
                    message = "Live API data loaded successfully.",
                    latitude = result.latitude ?: latitude,
                    longitude = result.longitude ?: longitude,
                    pm10 = current?.pm10,
                    pm25 = current?.pm25,
                    carbonMonoxide = current?.carbonMonoxide,
                    nitrogenDioxide = current?.nitrogenDioxide,
                    time = current?.time,
                    usedFallbackLocation = usedFallbackLocation
                )
            } catch (e: Exception) {
                _airQuality.value = AirQualityUiState(
                    isLoading = false,
                    message = "API failed: ${e.message}",
                    latitude = latitude,
                    longitude = longitude,
                    usedFallbackLocation = usedFallbackLocation
                )
            }
        }
    }

    // ── Firebase Firestore cloud persistence ─────────────────────────────────
    // Cloud collection for chat records: cloud_chat_messages

    // ── Device ID and local notification helpers ─────────────────────────────

    private fun currentDeviceId(): String {
        return Settings.Secure.getString(
            getApplication<Application>().contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "unknown-device"
    }

    private fun createNotificationChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "communilink_remote_updates",
                "CommuniLink Remote Updates",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for remote chat messages and community notices"
            }

            val manager = getApplication<Application>()
                .getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    @SuppressLint("MissingPermission")
    private fun showRemoteNotification(title: String, body: String, notificationId: Int) {
        val app = getApplication<Application>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        createNotificationChannelIfNeeded()

        val notification = NotificationCompat.Builder(app, "communilink_remote_updates")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(app).notify(notificationId, notification)
    }

    // ── Firebase Firestore remote listeners ──────────────────────────────────

    private fun startCloudChatListener() {
        cloudChatListener = Firebase.firestore
            .collection("cloud_chat_messages")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    _cloudMessage.value = "Firestore error: ${error.message}"
                    return@addSnapshotListener
                }

                val list = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(CloudChatMessage::class.java)?.copy(id = doc.id)
                }?.sortedByDescending { it.createdAt } ?: emptyList()

                val currentDevice = currentDeviceId()
                val notificationWindowStart = appStartTime - 10 * 60 * 1000L

                val newRemoteMessages = list.filter { msg ->
                    val key = msg.id.ifBlank { "${msg.conversation}-${msg.sender}-${msg.createdAt}" }
                    val isRemote = msg.deviceId.isNotBlank() && msg.deviceId != currentDevice
                    val isRecentEnough = msg.createdAt >= notificationWindowStart
                    isRemote && isRecentEnough && !notifiedChatIds.contains(key)
                }

                newRemoteMessages.forEach { msg ->
                    val key = msg.id.ifBlank { "${msg.conversation}-${msg.sender}-${msg.createdAt}" }
                    notifiedChatIds.add(key)
                    showRemoteNotification(
                        title = "New message from ${msg.sender}",
                        body = "${msg.conversation}: ${msg.content}",
                        notificationId = (msg.createdAt % Int.MAX_VALUE).toInt()
                    )
                }

                if (firstChatSnapshot) {
                    firstChatSnapshot = false
                }

                lastChatCreatedAt = maxOf(
                    lastChatCreatedAt,
                    list.maxOfOrNull { it.createdAt } ?: lastChatCreatedAt
                )

                _cloudChatMessages.value = list
                _cloudMessage.value = "Cloud synced: ${list.size} chat record(s)."
            }
    }

    private fun startCloudNoticeListener() {
        cloudNoticeListener = Firebase.firestore
            .collection("community_notices")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    _noticeCloudMessage.value = "Firestore notice error: ${error.message}"
                    return@addSnapshotListener
                }

                val list = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(CloudCommunityNotice::class.java)?.copy(id = doc.id)
                }?.sortedByDescending { it.createdAt } ?: emptyList()

                val currentDevice = currentDeviceId()
                val notificationWindowStart = appStartTime - 10 * 60 * 1000L

                val newRemoteNotices = list.filter { notice ->
                    val key = notice.id.ifBlank { "${notice.title}-${notice.author}-${notice.createdAt}" }
                    val isRemote = notice.deviceId.isNotBlank() && notice.deviceId != currentDevice
                    val isRecentEnough = notice.createdAt >= notificationWindowStart
                    isRemote && isRecentEnough && !notifiedNoticeIds.contains(key)
                }

                newRemoteNotices.forEach { notice ->
                    val key = notice.id.ifBlank { "${notice.title}-${notice.author}-${notice.createdAt}" }
                    notifiedNoticeIds.add(key)
                    showRemoteNotification(
                        title = "New community notice: ${notice.title}",
                        body = notice.content,
                        notificationId = (notice.createdAt % Int.MAX_VALUE).toInt()
                    )
                }

                if (firstNoticeSnapshot) {
                    firstNoticeSnapshot = false
                }

                lastNoticeCreatedAt = maxOf(
                    lastNoticeCreatedAt,
                    list.maxOfOrNull { it.createdAt } ?: lastNoticeCreatedAt
                )

                _cloudCommunityNotices.value = list
                _noticeCloudMessage.value = "Remote notices synced: ${list.size} notice(s)."
            }
    }

    override fun onCleared() {
        super.onCleared()
        cloudChatListener?.remove()
        cloudNoticeListener?.remove()
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// LAYER 6 ── MAIN ACTIVITY
// ══════════════════════════════════════════════════════════════════════════════

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Android 13+ needs notification permission for local notification alerts.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }

        setContent {
            var isDark by remember { mutableStateOf(true) }
            HappyBirthdayTheme(darkTheme = isDark) {
                val nav = rememberNavController()
                val vm: AppViewModel = viewModel()
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    NavHost(nav, startDestination = "chats") {
                        composable("chats")       { HomeScreen(nav, isDark) { isDark = !isDark } }
                        composable("chat/{name}") { ChatDetailScreen(nav, it.arguments?.getString("name") ?: "", vm) }
                        composable("me")          { MeScreen(nav, vm) }
                        composable("edit")        { EditScreen(nav, vm) }
                        composable("board")       { CommunityBoardScreen(nav, vm) }
                        composable("air")         { AirQualityScreen(nav, vm) }
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// UI LAYER ── SHARED COMPONENTS
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun BottomBar(nav: NavController) {
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        val items = listOf(
            Triple("chats", Icons.Default.Email, "Chats"),
            Triple("board", Icons.Default.Home, "Notice"),
            Triple("air", Icons.Default.LocationOn, "Air"),
            Triple("me", Icons.Default.Person, "Me")
        )
        items.forEach { (dest, icon, label) ->
            NavigationBarItem(
                icon = { Icon(icon, null) },
                label = { Text(label) },
                selected = route == dest,
                onClick = { nav.navigate(dest) { launchSingleTop = true } },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    }
}

@Composable
fun Project2InfoCard(
    title: String,
    body: String,
    color: Color = Color(0xFFE3F2FD),
    textColor: Color = Color(0xFF1F2937)
) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color, contentColor = textColor)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = textColor)
            Spacer(Modifier.height(4.dp))
            Text(body, fontSize = 12.sp, color = textColor)
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// SCREEN 1 ── CHAT LIST
// ══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(nav: NavController, isDark: Boolean, onToggle: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var confirmed by remember { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val contacts = remember {
        listOf("Alice", "Bob", "Charlie", "Community Group", "Block C Residents", "Mom", "Dad", "Best Friend")
    }
    val filtered = if (confirmed.isEmpty()) contacts else contacts.filter { it.contains(confirmed, true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "CommuniLink", Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center, fontSize = 18.sp, fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onToggle) {
                        Icon(if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        },
        bottomBar = { BottomBar(nav) }
    ) { pv ->
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(pv)
        ) {
            Surface(Modifier.fillMaxWidth(), color = Color(0xFF9C27B0)) {
                Text(
                    "🏙️ SDG 11: Building Sustainable & Connected Communities",
                    Modifier.padding(12.dp, 6.dp), fontSize = 12.sp, color = Color.White
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(12.dp, 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    query, { query = it },
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp)),
                    placeholder = { Text("Search contact...") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        confirmed = query; focus.clearFocus()
                    }),
                    singleLine = true
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    { confirmed = query; focus.clearFocus() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)),
                    shape = RoundedCornerShape(4.dp)
                ) { Text("Search", color = Color.White) }
            }
            if (confirmed.isNotEmpty())
                Text("Results for \"$confirmed\"", Modifier.padding(16.dp, 6.dp), fontWeight = FontWeight.Bold)
            LazyColumn(Modifier.fillMaxSize()) {
                items(filtered) { name -> ContactCard(name) { nav.navigate("chat/$name") } }
            }
        }
    }
}

@Composable
fun ContactCard(name: String, onClick: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        Modifier
            .fillMaxWidth()
            .padding(8.dp, 4.dp)
            .animateContentSize(alignment = Alignment.TopCenter)
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(100.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    Alignment.Center
                ) {
                    Text(name.take(1), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text("Last message from $name", fontSize = 13.sp, color = Color.Gray)
                }
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    null, tint = Color.Gray
                )
            }
            if (expanded) {
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick, Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
                ) { Text("Send Message", color = Color.White) }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// SCREEN 2 ── CHAT DETAIL
// ══════════════════════════════════════════════════════════════════════════════
// SCREEN 2 ── CHAT DETAIL / FIRESTORE REMOTE + ROOM BACKUP
// ══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(nav: NavController, name: String, vm: AppViewModel) {
    var text by remember { mutableStateOf("") }

    // Room local backup
    val allLocalMessages by vm.messages.collectAsState()
    val localMessages = allLocalMessages.filter { it.conversation == name }

    // Firestore remote messages shared by all devices using the same Firebase project
    val allCloudMessages by vm.cloudChatMessages.collectAsState()
    val cloudMessages = allCloudMessages
        .filter { it.conversation == name }
        .sortedBy { it.createdAt }

    val profile by vm.profile.collectAsState()
    val displayMessages = cloudMessages

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("$name • Cloud Chat") },
                navigationIcon = {
                    IconButton({ nav.popBackStack() }) { Icon(Icons.Default.ArrowBack, null) }
                }
            )
        }
    ) { pv ->
        Column(
            Modifier
                .padding(pv)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (displayMessages.isEmpty() && localMessages.isEmpty()) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No cloud messages yet. Send a message.\nIt will be saved to Room and Firestore.",
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(Modifier.weight(1f).padding(16.dp)) {
                    if (displayMessages.isNotEmpty()) {
                        items(displayMessages, key = { it.id.ifBlank { it.createdAt.toString() } }) { msg ->
                            CloudChatBubble(
                                sender = msg.sender,
                                content = msg.content,
                                isMe = msg.sender == profile.name || msg.sender == "Me"
                            )
                        }
                    } else {
                        // Offline fallback: show Room backup if Firestore has not loaded yet.
                        items(localMessages, key = { it.id }) { msg ->
                            CloudChatBubble(
                                sender = msg.sender,
                                content = msg.content,
                                isMe = msg.sender == profile.name || msg.sender == "Me"
                            )
                        }
                    }
                }
            }

            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    text, { text = it },
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp)),
                    placeholder = { Text("Type a message...") }
                )
                IconButton({
                    vm.sendMessage(name, text)
                    text = ""
                }) {
                    Icon(Icons.Default.Send, null, tint = Color(0xFF1976D2))
                }
            }
        }
    }
}

@Composable
fun CloudChatBubble(sender: String, content: String, isMe: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (isMe) Color(0xFF95EC69) else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(Modifier.padding(12.dp)) {
                if (!isMe) {
                    Text(
                        sender,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1976D2)
                    )
                }
                Text(content)
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// SCREEN 3 ── ME
// ══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeScreen(nav: NavController, vm: AppViewModel) {
    val profile by vm.profile.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Me", fontWeight = FontWeight.Bold) }) },
        bottomBar = { BottomBar(nav) }
    ) { pv ->
        Column(
            Modifier
                .padding(pv)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(24.dp)
                    .clickable { nav.navigate("edit") },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    Alignment.Center
                ) {
                    Text(profile.name.take(1), fontSize = 30.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(profile.name, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(profile.bio, color = Color.Gray, fontSize = 14.sp)
                    Text("📞 ${profile.phone}", color = Color.Gray, fontSize = 13.sp)
                }
                Icon(Icons.Default.KeyboardArrowRight, null, tint = Color.Gray)
            }

            Spacer(Modifier.height(8.dp))

            Card(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp, 8.dp)
                    .clickable { nav.navigate("board") },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Home, null, tint = Color(0xFF1976D2), modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Community Notices", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Firestore remote notices", fontSize = 13.sp, color = Color.Gray)
                    }
                    Icon(Icons.Default.KeyboardArrowRight, null, tint = Color.Gray)
                }
            }

            Card(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp, 4.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "🏙️ SDG 11 – Sustainable Cities & Communities",
                        fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1565C0)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "This app supports remote chat, remote community notices, Room offline backup, Firestore cloud records, GPS location, and live air-quality API data.",
                        fontSize = 12.sp, color = Color(0xFF1976D2)
                    )
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// SCREEN 4 ── EDIT PROFILE
// ══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(nav: NavController, vm: AppViewModel) {
    val profile by vm.profile.collectAsState()

    var name by remember(profile.name) { mutableStateOf(profile.name) }
    var bio by remember(profile.bio) { mutableStateOf(profile.bio) }
    var phone by remember(profile.phone) { mutableStateOf(profile.phone) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Profile") },
                navigationIcon = {
                    IconButton({ nav.popBackStack() }) { Icon(Icons.Default.ArrowBack, null) }
                }
            )
        }
    ) { pv ->
        Column(Modifier.padding(pv).padding(16.dp)) {
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Name") })
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(bio, { bio = it }, Modifier.fillMaxWidth(), label = { Text("Bio (e.g. Block B Resident)") })
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(phone, { phone = it }, Modifier.fillMaxWidth(), label = { Text("Phone") })
            Spacer(Modifier.height(20.dp))
            Button(
                { vm.updateProfile(name, bio, phone); nav.popBackStack() },
                Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF673AB7))
            ) { Text("Save Profile", color = Color.White) }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// SCREEN 5 ── COMMUNITY NOTICES / FIRESTORE REMOTE + ROOM BACKUP
// ══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityBoardScreen(nav: NavController, vm: AppViewModel) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var showForm by remember { mutableStateOf(false) }

    // Main display source: Firestore remote database.
    val cloudNotices by vm.cloudCommunityNotices.collectAsState()
    val noticeCloudMessage by vm.noticeCloudMessage.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Community Notices - Firestore", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton({ nav.popBackStack() }) { Icon(Icons.Default.ArrowBack, null) }
                },
                actions = {
                    IconButton({ showForm = !showForm }) {
                        Icon(if (showForm) Icons.Default.Close else Icons.Default.Add, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        },
        bottomBar = { BottomBar(nav) }
    ) { pv ->
        Column(
            Modifier
                .padding(pv)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (showForm) {
                Surface(
                    Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Upload Community Notice", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            title,
                            { title = it },
                            Modifier.fillMaxWidth(),
                            label = { Text("Notice Title") },
                            singleLine = true
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            content,
                            { content = it },
                            Modifier.fillMaxWidth(),
                            label = { Text("Notice Details") },
                            minLines = 2
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(
                            {
                                vm.addNotice(title, content)
                                title = ""
                                content = ""
                                showForm = false
                            },
                            Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
                        ) {
                            Icon(Icons.Default.CloudUpload, null, tint = Color.White)
                            Spacer(Modifier.width(6.dp))
                            Text("Upload Notice to Firestore", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Project2InfoCard(
                title = "Remote Community Notices",
                body = "This list reads from Firestore collection: community_notices. Notices uploaded from another device using the same Firebase project will appear here and trigger a phone notification if notification permission is allowed.",
                color = Color(0xFFF3E5F5)
            )

            Text(
                noticeCloudMessage,
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                fontSize = 12.sp,
                color = Color.Gray
            )

            if (cloudNotices.isNotEmpty()) {
                val latestNotice = cloudNotices.first()
                Card(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "Latest remote notice",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color(0xFFE65100)
                        )
                        Text(
                            latestNotice.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFF1F2937)
                        )
                        Text(
                            latestNotice.content,
                            fontSize = 12.sp,
                            color = Color(0xFF1F2937)
                        )
                    }
                }
            }

            Row(Modifier.padding(16.dp, 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Remote Notices - Firestore", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.width(8.dp))
                Surface(color = Color(0xFF673AB7), shape = RoundedCornerShape(100.dp)) {
                    Text(
                        "${cloudNotices.size}",
                        Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (cloudNotices.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No remote notices yet.\nUpload a notice from this device or another device.\nBoth devices must use the same Firebase project.",
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        fontSize = 13.sp
                    )
                }
            } else {
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                    items(cloudNotices, key = { it.id.ifBlank { it.createdAt.toString() } }) { notice ->
                        CloudCommunityNoticeCard(notice)
                    }
                }
            }
        }
    }
}

@Composable
fun CloudCommunityNoticeCard(notice: CloudCommunityNotice) {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    val time = sdf.format(Date(notice.createdAt))

    Card(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(notice.title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text("Uploaded by ${notice.author} • $time", fontSize = 11.sp, color = Color.Gray)
            Spacer(Modifier.height(6.dp))
            Text(notice.content, fontSize = 13.sp)
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// SCREEN 6 ── GPS SENSOR + RETROFIT API
// ══════════════════════════════════════════════════════════════════════════════

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AirQualityScreen(nav: NavController, vm: AppViewModel) {
    val context = LocalContext.current
    val state by vm.airQuality.collectAsState()
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    fun fetchUsingGpsOrFallback() {
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    vm.fetchAirQuality(location.latitude, location.longitude, usedFallbackLocation = false)
                } else {
                    // Fallback coordinate near UKM, useful for emulator or first launch.
                    vm.fetchAirQuality(2.9264, 101.7800, usedFallbackLocation = true)
                }
            }
            .addOnFailureListener {
                vm.fetchAirQuality(2.9264, 101.7800, usedFallbackLocation = true)
            }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        if (granted) {
            fetchUsingGpsOrFallback()
        } else {
            vm.fetchAirQuality(2.9264, 101.7800, usedFallbackLocation = true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Air Quality - GPS + API", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            )
        },
        bottomBar = { BottomBar(nav) }
    ) { pv ->
        LazyColumn(
            Modifier
                .padding(pv)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Project2InfoCard(
                    title = "Sensor + Internet Data",
                    body = "GPS gets latitude/longitude. Retrofit sends them to Open-Meteo REST API and loads live air-quality data."
                )
            }

            item {
                Button(
                    onClick = {
                        if (hasLocationPermission()) {
                            fetchUsingGpsOrFallback()
                        } else {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = "GPS", tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Use GPS and Load Live API Data", color = Color.White)
                }
            }

            item {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Status", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(state.message, fontSize = 13.sp)
                        if (state.isLoading) {
                            Spacer(Modifier.height(12.dp))
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                        if (state.usedFallbackLocation) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Using UKM fallback coordinate because GPS last location was unavailable.",
                                color = Color(0xFFE65100),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Current Location", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text("Latitude: ${state.latitude?.let { "%.5f".format(it) } ?: "-"}")
                        Text("Longitude: ${state.longitude?.let { "%.5f".format(it) } ?: "-"}")
                        Text("API Time: ${state.time ?: "-"}")
                    }
                }
            }

            item { Text("Live Air-Quality Data", fontWeight = FontWeight.Bold, fontSize = 18.sp) }
            item { AirMetricCard("PM2.5", state.pm25?.let { "%.2f µg/m³".format(it) } ?: "-", "Fine particles. Lower is better.") }
            item { AirMetricCard("PM10", state.pm10?.let { "%.2f µg/m³".format(it) } ?: "-", "Dust and coarse particle pollution.") }
            item { AirMetricCard("Carbon Monoxide", state.carbonMonoxide?.let { "%.2f µg/m³".format(it) } ?: "-", "Traffic-related air pollution indicator.") }
            item { AirMetricCard("Nitrogen Dioxide", state.nitrogenDioxide?.let { "%.2f µg/m³".format(it) } ?: "-", "Urban combustion and vehicle emission indicator.") }
        }
    }
}

@Composable
fun AirMetricCard(title: String, value: String, description: String) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(value, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Color(0xFF1976D2))
            Spacer(Modifier.height(4.dp))
            Text(description, fontSize = 12.sp, color = Color.Gray)
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// PREVIEWS
// ══════════════════════════════════════════════════════════════════════════════

@Preview(showBackground = true, name = "1. Home – Chat List")
@Composable
fun DefaultPreview() {
    HappyBirthdayTheme { HomeScreen(rememberNavController(), false) {} }
}
