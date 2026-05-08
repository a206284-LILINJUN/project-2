package com.example.happybirthday

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.happybirthday.ui.theme.HappyBirthdayTheme

// ── 1. 数据模型 (Data Models) ───────────────────────────────────────────
data class UserProfile(val name: String = "My Name", val bio: String = "Community Member", val phone: String = "123-456789")
data class ChatMessage(val sender: String, val content: String)
data class Notice(val author: String, val title: String, val content: String)

// ── 2. 逻辑层 (ViewModel) ─────────────────────────────────────────────
class AppViewModel : ViewModel() {
    var profile by mutableStateOf(UserProfile()); private set
    var messages by mutableStateOf(listOf(ChatMessage("Alice", "Hi neighbour!"), ChatMessage("Bob", "Community BBQ this Sunday!"))); private set
    var notices by mutableStateOf(listOf(Notice("Admin", "Road Closure", "Jalan Utama closed Saturday 9am-5pm."), Notice("Alice", "Lost Cat", "Orange tabby missing near Block C."))); private set

    fun updateProfile(n: String, b: String, p: String) { profile = profile.copy(name = n, bio = b, phone = p) }
    fun sendMessage(text: String) { if (text.isNotBlank()) messages = messages + ChatMessage("Me", text) }
    fun addNotice(title: String, content: String) { if (title.isNotBlank() && content.isNotBlank()) notices = notices + Notice(profile.name, title, content) }
}

// ── 3. 入口点 (MainActivity) ──────────────────────────────────────────
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var isDark by remember { mutableStateOf(false) }
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
                    }
                }
            }
        }
    }
}

// ── 4. 底部导航栏 (Bottom Bar) ─────────────────────────────────────────
@Composable
fun BottomBar(nav: NavController) {
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        listOf("chats" to Icons.Default.Email, "board" to Icons.Default.Home, "me" to Icons.Default.Person)
            .forEachIndexed { i, (dest, icon) ->
                NavigationBarItem(
                    icon = { Icon(icon, null) },
                    label = { Text(listOf("Chats", "Board", "Me")[i]) },
                    selected = route == dest,
                    onClick = { nav.navigate(dest) },
                    colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer)
                )
            }
    }
}

// ── 5. 屏幕 1: 聊天列表 (Home Screen) ───────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(nav: NavController, isDark: Boolean, onToggle: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var confirmed by remember { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val contacts = remember { listOf("Alice", "Bob", "Charlie", "Community Group", "Block C Residents", "Mom", "Dad", "Best Friend") }
    val filtered = if (confirmed.isEmpty()) contacts else contacts.filter { it.contains(confirmed, true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CommuniLink", Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                actions = { IconButton(onToggle) { Icon(if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            )
        },
        bottomBar = { BottomBar(nav) }
    ) { pv ->
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(pv)) {
            Surface(Modifier.fillMaxWidth(), color = Color(0xFF1976D2)) {
                Text("🏙️ SDG 11: Building Sustainable & Connected Communities", Modifier.padding(12.dp, 6.dp), fontSize = 12.sp, color = Color.White)
            }
            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextField(query, { query = it }, Modifier.weight(1f).clip(RoundedCornerShape(8.dp)),
                    placeholder = { Text("Search contact...") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { confirmed = query; focus.clearFocus() }),
                    singleLine = true)
                Spacer(Modifier.width(8.dp))
                Button({ confirmed = query; focus.clearFocus() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)), shape = RoundedCornerShape(4.dp)) {
                    Text("Search", color = Color.White)
                }
            }
            if (confirmed.isNotEmpty()) Text("Results for \"$confirmed\"", Modifier.padding(16.dp, 6.dp), fontWeight = FontWeight.Bold)
            LazyColumn(Modifier.fillMaxSize()) {
                items(filtered) { name -> ContactCard(name) { nav.navigate("chat/$name") } }
            }
        }
    }
}

@Composable
fun ContactCard(name: String, onClick: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().padding(8.dp, 4.dp).animateContentSize(alignment = Alignment.TopCenter).clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(100.dp)).background(MaterialTheme.colorScheme.primaryContainer), Alignment.Center) {
                    Text(name.take(1), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text("Last message from $name", fontSize = 13.sp, color = Color.Gray)
                }
                Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null, tint = Color.Gray)
            }
            if (expanded) {
                Spacer(Modifier.height(10.dp))
                Button(onClick, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))) {
                    Text("Send Message", color = Color.White)
                }
            }
        }
    }
}

// ── 6. 屏幕 2: 聊天详情 (Chat Detail Screen) ───────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(nav: NavController, name: String, vm: AppViewModel) {
    var text by remember { mutableStateOf("") }
    Scaffold(topBar = { TopAppBar(title = { Text(name) }, navigationIcon = { IconButton({ nav.popBackStack() }) { Icon(Icons.Default.ArrowBack, null) } }) }) { pv ->
        Column(Modifier.padding(pv).fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            LazyColumn(Modifier.weight(1f).padding(16.dp)) {
                items(vm.messages) { msg ->
                    val isMe = msg.sender == "Me"
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start) {
                        Surface(color = if (isMe) Color(0xFF95EC69) else MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp)) {
                            Text(msg.content, Modifier.padding(12.dp))
                        }
                    }
                }
            }
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextField(text, { text = it }, Modifier.weight(1f).clip(RoundedCornerShape(8.dp)), placeholder = { Text("Type a message...") })
                IconButton({ vm.sendMessage(text); text = "" }) { Icon(Icons.Default.Send, null, tint = Color(0xFF1976D2)) }
            }
        }
    }
}

// ── 7. 屏幕 3: 个人中心 (Me Screen) ────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeScreen(nav: NavController, vm: AppViewModel) {
    Scaffold(topBar = { TopAppBar(title = { Text("Me", fontWeight = FontWeight.Bold) }) }, bottomBar = { BottomBar(nav) }) { pv ->
        Column(Modifier.padding(pv).fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)) {
            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(24.dp).clickable { nav.navigate("edit") }, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.primaryContainer), Alignment.Center) {
                    Text(vm.profile.name.take(1), fontSize = 30.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(vm.profile.name, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(vm.profile.bio, color = Color.Gray, fontSize = 14.sp)
                    Text("📞 ${vm.profile.phone}", color = Color.Gray, fontSize = 13.sp)
                }
                Icon(Icons.Default.KeyboardArrowRight, null, tint = Color.Gray)
            }
            Spacer(Modifier.height(8.dp))
            Card(Modifier.fillMaxWidth().padding(16.dp, 8.dp).clickable { nav.navigate("board") },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Home, null, tint = Color(0xFF1976D2), modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Community Board", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("View & post community notices", fontSize = 13.sp, color = Color.Gray)
                    }
                    Icon(Icons.Default.KeyboardArrowRight, null, tint = Color.Gray)
                }
            }
            Card(Modifier.fillMaxWidth().padding(16.dp, 4.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))) {
                Column(Modifier.padding(16.dp)) {
                    Text("🏙️ SDG 11 – Sustainable Cities & Communities", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1565C0))
                    Spacer(Modifier.height(4.dp))
                    Text("In Malaysian urban areas, residents in the same neighbourhood often don't communicate. This app bridges that gap by enabling real-time messaging and community notice sharing, fostering a more connected and sustainable community.", fontSize = 12.sp, color = Color(0xFF1976D2))
                }
            }
        }
    }
}

// ── 8. 屏幕 4: 编辑资料 (Edit Screen) ──────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(nav: NavController, vm: AppViewModel) {
    var name by remember { mutableStateOf(vm.profile.name) }
    var bio by remember { mutableStateOf(vm.profile.bio) }
    var phone by remember { mutableStateOf(vm.profile.phone) }
    Scaffold(topBar = { TopAppBar(title = { Text("Edit Profile") }, navigationIcon = { IconButton({ nav.popBackStack() }) { Icon(Icons.Default.ArrowBack, null) } }) }) { pv ->
        Column(Modifier.padding(pv).padding(16.dp)) {
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Name") })
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(bio, { bio = it }, Modifier.fillMaxWidth(), label = { Text("Bio (e.g. Block B Resident)") })
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(phone, { phone = it }, Modifier.fillMaxWidth(), label = { Text("Phone") })
            Spacer(Modifier.height(20.dp))
            Button({ vm.updateProfile(name, bio, phone); nav.popBackStack() }, Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))) {
                Text("Save Profile", color = Color.White)
            }
        }
    }
}

// ── 9. 屏幕 5: 社区公告板 (Community Board Screen) ──────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityBoardScreen(nav: NavController, vm: AppViewModel) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var showForm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Community Board", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton({ nav.popBackStack() }) { Icon(Icons.Default.ArrowBack, null) } },
                actions = { IconButton({ showForm = !showForm }) { Icon(if (showForm) Icons.Default.Close else Icons.Default.Add, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            )
        },
        bottomBar = { BottomBar(nav) }
    ) { pv ->
        Column(Modifier.padding(pv).fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (showForm) {
                Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Post a Notice", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("Notice Title") }, singleLine = true)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(content, { content = it }, Modifier.fillMaxWidth(), label = { Text("Details") }, minLines = 2)
                        Spacer(Modifier.height(10.dp))
                        Button({
                            vm.addNotice(title, content)
                            title = ""; content = ""; showForm = false
                        }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))) {
                            Icon(Icons.Default.Send, null, tint = Color.White)
                            Spacer(Modifier.width(6.dp))
                            Text("Post Notice", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Row(Modifier.padding(16.dp, 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Notices", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.width(8.dp))
                Surface(color = Color(0xFF1976D2), shape = RoundedCornerShape(100.dp)) {
                    Text("${vm.notices.size}", Modifier.padding(horizontal = 8.dp, vertical = 2.dp), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                items(vm.notices.reversed()) { notice ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(32.dp).clip(RoundedCornerShape(100.dp)).background(MaterialTheme.colorScheme.primaryContainer), Alignment.Center) {
                                    Text(notice.author.take(1), fontSize = 14.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(notice.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text("Posted by ${notice.author}", fontSize = 11.sp, color = Color.Gray)
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(notice.content, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

// ── 10. 预览区域 (Preview Section) ────────────────────────────────────

@Preview(showBackground = true, name = "1. 主页-聊天列表")
@Composable
fun DefaultPreview() {
    HappyBirthdayTheme { HomeScreen(rememberNavController(), false) {} }
}

@Preview(showBackground = true, name = "2. 聊天详情页")
@Composable
fun ChatDetailPreview() {
    HappyBirthdayTheme { ChatDetailScreen(rememberNavController(), "Alice", AppViewModel()) }
}

@Preview(showBackground = true, name = "3. 个人中心")
@Composable
fun MeScreenPreview() {
    HappyBirthdayTheme { MeScreen(rememberNavController(), AppViewModel()) }
}

@Preview(showBackground = true, name = "4. 编辑资料")
@Composable
fun EditScreenPreview() {
    HappyBirthdayTheme { EditScreen(rememberNavController(), AppViewModel()) }
}

@Preview(showBackground = true, name = "5. 社区公告板")
@Composable
fun CommunityBoardPreview() {
    HappyBirthdayTheme { CommunityBoardScreen(rememberNavController(), AppViewModel()) }
}

@Preview(showBackground = true, name = "6. 主页-深色模式", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
fun DarkHomePreview() {
    HappyBirthdayTheme(darkTheme = true) { HomeScreen(rememberNavController(), true) {} }
}