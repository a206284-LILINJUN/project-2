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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.happybirthday.ui.theme.HappyBirthdayTheme
import kotlinx.coroutines.newFixedThreadPoolContext

// ==========================================
// TASK 2: 数据与状态管理 (ViewModel)
// ==========================================
data class UserProfile(
    val name: String = "My Name",
    val bio: String = "Learning Android Compose!",
    val phone: String = "123-456789"
)

data class ChatMessage(val sender: String, val content: String)

class WeChatViewModel : ViewModel() {
    var profile by mutableStateOf(UserProfile())
        private set

    var messages by mutableStateOf(listOf(
        ChatMessage("Mom", "Did you eat?"),
        ChatMessage("Project Manager", "How is Lab 4 going?")
    ))
        private set

    fun updateProfile(newName: String, newBio: String, newPhone: String) {
        profile = profile.copy(name = newName, bio = newBio, phone = newPhone)
    }

    fun sendMessage(text: String) {
        if (text.isNotBlank()) messages = messages + ChatMessage("Me", text)
    }
}

// ==========================================
// 入口与 Navigation (Task 1)
// ==========================================
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var isDarkMode by remember { mutableStateOf(false) }

            HappyBirthdayTheme(darkTheme = isDarkMode) {
                val navController = rememberNavController()
                val vm: WeChatViewModel = viewModel()

                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    NavHost(navController = navController, startDestination = "chats") {
                        // 1. 原汁原味的主页
                        composable("chats") { WeChatMainScreen(navController, isDarkMode, { isDarkMode = !isDarkMode }) }
                        // 2. 聊天对话框
                        composable("chat/{name}") { backStack ->
                            val name = backStack.arguments?.getString("name") ?: ""
                            ChatDetailScreen(navController, name, vm)
                        }
                        // 3. 个人资料 (Me)
                        composable("me") { MeProfileScreen(navController, vm) }
                        // 4. 编辑资料
                        composable("edit") { EditProfileScreen(navController, vm) }
                    }
                }
            }
        }
    }
}

// ==========================================
// SCREEN 1: 恢复你最原本的微信主页
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeChatMainScreen(navController: NavController, isDark: Boolean, onThemeToggle: () -> Unit) {
    var searchQuery by remember { mutableStateOf("") }
    var confirmedSearch by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    val allContacts = remember {
        listOf("Mom", "Dad", "Android Dev Group", "Project Manager", "Best Friend", "Alice", "Bob", "Charlie", "HH")
    }

    val filteredContacts = if (confirmedSearch.isEmpty()) allContacts else allContacts.filter { it.contains(confirmedSearch, ignoreCase = true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "WeChat", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onThemeToggle) {
                        Icon(imageVector = if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode, contentDescription = "Toggle Theme")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            )
        },
        bottomBar = { WeChatBottomBar(navController) }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(paddingValues)) {

            // 完全保留你的搜索栏
            Row(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search contact...", fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.weight(1f).heightIn(min = 40.dp).clip(RoundedCornerShape(8.dp)),
                    colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { confirmedSearch = searchQuery; focusManager.clearFocus() }),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = { confirmedSearch = searchQuery; focusManager.clearFocus() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFABD468)), shape = RoundedCornerShape(4.dp)) {
                    Text("Search", color = Color.White)
                }
            }

            if (confirmedSearch.isNotEmpty()) {
                Text(text = "Showing results for \"$confirmedSearch\"", modifier = Modifier.padding(16.dp, 8.dp), fontWeight = FontWeight.Bold)
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(filteredContacts) { contact ->
                    // 传入 navController，准备跳转
                    ChatItem(name = contact, onChatClick = { navController.navigate("chat/$contact") })
                }
            }
        }
    }
}

// ==========================================
// 完全恢复你原本的 ChatItem (带展开动画)
// ==========================================
@Composable
fun ChatItem(name: String, onChatClick: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .animateContentSize(alignment = Alignment.TopCenter) // 恢复你的动画
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 恢复你的圆形头像
                Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(100.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                    Text(text = name.take(1), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = name, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                    Text(text = "Last message from $name", fontSize = 13.sp, color = Color.Gray)
                }
                // 恢复你的下拉箭头
                Icon(imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = null, tint = Color.Gray)
            }

            // 展开后显示详情，并增加一个进入聊天的按钮
            if (expanded) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = "Detail: This is $name's profile. Material 3 Card expands smoothly.", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onChatClick, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFABD468))) {
                    Text("Send Message", color = Color.White)
                }
            }
        }
    }
}

// ==========================================
// 底部导航栏 (只保留 Chats 和 Me)
// ==========================================
@Composable
fun WeChatBottomBar(navController: NavController) {
    val items = listOf("Chats", "Me")
    val icons = listOf(Icons.Default.Email, Icons.Default.Person)

    // 获取当前路由，以高亮正确的图标
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        items.forEachIndexed { index, item ->
            NavigationBarItem(
                icon = { Icon(icons[index], contentDescription = item) },
                label = { Text(item) },
                selected = (currentRoute == "chats" && index == 0) || (currentRoute == "me" && index == 1),
                onClick = {
                    if (index == 0) navController.navigate("chats")
                    if (index == 1) navController.navigate("me")
                },
                colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer)
            )
        }
    }
}

// ==========================================
// SCREEN 2: 发短信的聊天页面
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(navController: NavController, name: String, vm: WeChatViewModel) {
    var text by remember { mutableStateOf("") }
    Scaffold(topBar = { TopAppBar(title = { Text(name) }, navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, null) } }) }) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            LazyColumn(modifier = Modifier.weight(1f).padding(16.dp)) {
                items(vm.messages) { msg ->
                    val isMe = msg.sender == "Me"
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start) {
                        Surface(color = if (isMe) Color(0xFF95EC69) else MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp)) {
                            Text(msg.content, modifier = Modifier.padding(12.dp))
                        }
                    }
                }
            }
            Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextField(value = text, onValueChange = { text = it }, modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)), placeholder = { Text("Type a message...") })
                IconButton(onClick = { vm.sendMessage(text); text = "" }) { Icon(Icons.Default.Send, null, tint = Color(0xFFABD468)) }
            }
        }
    }
}

// ==========================================
// SCREEN 3: 个人资料页 (Me)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeProfileScreen(navController: NavController, vm: WeChatViewModel) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Me", fontWeight = FontWeight.Bold) }) },
        bottomBar = { WeChatBottomBar(navController) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)) {
            Row(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(24.dp).clickable { navController.navigate("edit") }, verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                    Text(vm.profile.name.take(1), fontSize = 30.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(vm.profile.name, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("Status: ${vm.profile.bio}", color = Color.Gray, fontSize = 14.sp)
                }
                Icon(Icons.Default.KeyboardArrowRight, null, tint = Color.Gray)
            }
        }
    }
}

// ==========================================
// SCREEN 4: 编辑个人资料
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(navController: NavController, vm: WeChatViewModel) {
    var name by remember { mutableStateOf(vm.profile.name) }
    var bio by remember { mutableStateOf(vm.profile.bio) }
    var phone by remember { mutableStateOf(vm.profile.phone) }
    Scaffold(topBar = { TopAppBar(title = { Text("Edit Profile") }, navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, null) } }) }) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(value = bio, onValueChange = { bio = it }, label = { Text("Status/Bio") }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone") }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = { vm.updateProfile(name, bio, phone); navController.popBackStack() }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFABD468))) {
                Text("Save", color = Color.White)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun WeChatMainScreenPreview() {
    HappyBirthdayTheme {
        WeChatMainScreen(
            navController = rememberNavController(),
            isDark = false,
            onThemeToggle = {}
        )
    }
}