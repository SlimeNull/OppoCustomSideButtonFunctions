package com.slimenull.customsidebuttonfunctions

import android.os.Bundle
import android.content.Context
import android.app.AlertDialog
import android.view.SoundEffectConstants
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.slimenull.customsidebuttonfunctions.data.SettingsStore
import com.slimenull.customsidebuttonfunctions.data.ShellRelayContract
import com.slimenull.customsidebuttonfunctions.model.ActionType
import com.slimenull.customsidebuttonfunctions.model.AppSettings
import com.slimenull.customsidebuttonfunctions.model.CommonAction
import com.slimenull.customsidebuttonfunctions.model.CustomActionSettings

private val AppBackground = Color(0xFFF3F7FD)
private val AppBlue = Color(0xFF347FE8)
private val AppBlueDark = Color(0xFF1954A6)
private val AppBlueSoft = Color(0xFFE7F1FF)
private val AppText = Color(0xFF152238)
private val AppMuted = Color(0xFF718198)
private val AppGreen = Color(0xFF2DAE78)

private val AppColors = lightColorScheme(
    primary = AppBlue,
    onPrimary = Color.White,
    background = AppBackground,
    onBackground = AppText,
    surface = Color.White,
    onSurface = AppText,
    surfaceVariant = Color(0xFFE9F0F8),
    onSurfaceVariant = AppMuted
)

private enum class BottomTab { HOME, SETTINGS }

private enum class GestureKind(val title: String, val subtitle: String) {
    SINGLE("单击", "按下并松开后立即触发"),
    DOUBLE("双击", "连续两次按下并松开"),
    LONG("长按", "按住并达到指定时间")
}

private sealed interface Route {
    data object Home : Route
    data object Settings : Route
    data object Feedback : Route
    data object Advanced : Route
    data object About : Route
    data class Gesture(val kind: GestureKind) : Route
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { CustomSideButtonApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomSideButtonApp() {
    val context = LocalContext.current
    val view = LocalView.current
    var settings by remember { mutableStateOf(SettingsStore.load(context)) }
    var route by remember { mutableStateOf<Route>(Route.Home) }

    fun persist(next: AppSettings) {
        settings = next
        SettingsStore.save(context, next)
    }

    val selectedTab = when (route) {
        Route.Settings, Route.About -> BottomTab.SETTINGS
        else -> BottomTab.HOME
    }
    val showBottomBar = route is Route.Home || route is Route.Settings || route is Route.About

    BackHandler(enabled = route !is Route.Home && route !is Route.Settings) {
        route = if (route is Route.About) Route.Settings else Route.Home
    }

    MaterialTheme(colorScheme = AppColors) {
        Scaffold(
            containerColor = AppBackground,
            bottomBar = {
                if (showBottomBar) {
                    NavigationBar(containerColor = Color.White) {
                        NavigationBarItem(
                            selected = selectedTab == BottomTab.HOME,
                            onClick = { clickSound(view); route = Route.Home },
                            icon = { Icon(Icons.Default.Home, contentDescription = "首页") },
                            label = { Text("首页") }
                        )
                        NavigationBarItem(
                            selected = selectedTab == BottomTab.SETTINGS,
                            onClick = { clickSound(view); route = Route.Settings },
                            icon = { Icon(Icons.Default.Settings, contentDescription = "设置") },
                            label = { Text("设置") }
                        )
                    }
                }
            }
        ) { padding ->
            when (val current = route) {
                Route.Home -> HomeScreen(settings, { route = it }, { persist(it) }, padding)
                Route.Settings -> SettingsScreen({ route = it }, padding)
                Route.About -> AboutScreen({ route = it }, padding)
                Route.Feedback -> FeedbackScreen(settings, { persist(it) }, { route = Route.Home }, padding)
                Route.Advanced -> AdvancedScreen(settings, { persist(it) }, { route = Route.Home }, padding)
                is Route.Gesture -> GestureScreen(current.kind, settings, { persist(it) }, { route = Route.Home }, padding)
            }
        }
    }
}

@Composable
private fun HomeScreen(
    settings: AppSettings,
    navigate: (Route) -> Unit,
    persist: (AppSettings) -> Unit,
    padding: PaddingValues
) {
    Column(Modifier.fillMaxSize().padding(padding)) {
        Text(
            "侧键功能设置",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                val view = LocalView.current
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Default.Tune)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("启用自定义侧键功能", fontWeight = FontWeight.SemiBold)
                            Text(if (settings.enabled) "当前状态：已启用" else "当前状态：已停用", style = MaterialTheme.typography.bodySmall, color = if (settings.enabled) AppGreen else AppMuted)
                        }
                        Switch(settings.enabled, { clickSound(view); persist(settings.copy(enabled = it)) })
                    }
                }
            }
            item { SectionLabel("按键事件设置") }
            item { GestureRow(GestureKind.SINGLE, settings.singleAction, settings.singleCustom, navigate) }
            item { GestureRow(GestureKind.DOUBLE, settings.doubleAction, settings.doubleCustom, navigate) }
            item { GestureRow(GestureKind.LONG, settings.longAction, settings.longCustom, navigate) }
            item { SectionLabel("其他设置") }
            item {
                SettingRow(Icons.Default.Vibration, "振动与提示", "配置振动时长和 Toast 提示") { navigate(Route.Feedback) }
            }
            item {
                SettingRow(Icons.Default.Settings, "高级设置", "设备输入与息屏行为") { navigate(Route.Advanced) }
            }
        }
    }
}

@Composable
private fun GestureRow(
    kind: GestureKind,
    action: ActionType,
    custom: CustomActionSettings,
    navigate: (Route) -> Unit
) {
    val title = when {
        action == ActionType.NONE -> "未配置"
        action == ActionType.COMMON_FUNCTION -> custom.commonAction.title
        else -> action.title
    }
    SettingRow(gestureIcon(kind), kind.title, title) {
        navigate(Route.Gesture(kind))
    }
}

@Composable
private fun GestureScreen(
    kind: GestureKind,
    settings: AppSettings,
    persist: (AppSettings) -> Unit,
    back: () -> Unit,
    padding: PaddingValues
) {
    val view = LocalView.current
    val context = LocalContext.current
    var sliderValue by remember(kind, settings) {
        mutableFloatStateOf(
            when (kind) {
                GestureKind.DOUBLE -> settings.doubleClickWindowMs.toFloat()
                GestureKind.LONG -> settings.longPressMs.toFloat()
                GestureKind.SINGLE -> 0f
            }
        )
    }
    Column(Modifier.fillMaxSize().padding(padding)) {
        BackTitle(kind.title, back)
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          item {
            IntroCard(
                icon = gestureIcon(kind),
                title = kind.title,
                description = when (kind) {
                    GestureKind.SINGLE -> "按下并松开后立即触发。如果未配置双击，则无需等待。"
                    GestureKind.DOUBLE -> "连续两次按下并松开，需要在指定的等待时间内完成第二次按下。"
                    GestureKind.LONG -> "按下并保持达到指定时间后触发，松开不会再次触发单击。"
                }
            )
          }
          if (kind != GestureKind.SINGLE) {
            item {
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(14.dp)) {
                        Text(if (kind == GestureKind.DOUBLE) "等待时间" else "按下时长", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Slider(
                                value = sliderValue,
                                onValueChange = { sliderValue = it },
                                onValueChangeFinished = {
                                    clickSound(view)
                                    val value = sliderValue.toLong()
                                    persist(if (kind == GestureKind.DOUBLE) settings.copy(doubleClickWindowMs = value) else settings.copy(longPressMs = value))
                                },
                                valueRange = if (kind == GestureKind.DOUBLE) 100f..1000f else 200f..3000f,
                                modifier = Modifier.weight(1f)
                            )
                            ValuePill("${sliderValue.toLong()} ms")
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(if (kind == GestureKind.DOUBLE) "100ms" else "200ms", style = MaterialTheme.typography.labelSmall, color = AppMuted)
                            Text(if (kind == GestureKind.DOUBLE) "1000ms" else "3000ms", style = MaterialTheme.typography.labelSmall, color = AppMuted)
                        }
                    }
                }
            }
          }
          item { SectionLabel("选择要执行的功能") }
          item {
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                val currentAction = selectedAction(kind, settings)
                val currentCustom = selectedCustom(kind, settings)
                val divider = @Composable { HorizontalDivider(color = Color(0xFFEAF0F7), modifier = Modifier.padding(start = 58.dp)) }

                Column {
                    ActionOption(ActionType.NONE, selected = currentAction == ActionType.NONE) {
                        persist(updateAction(kind, settings, ActionType.NONE))
                    }
                    divider()
                    ActionOption(ActionType.CYCLE_RINGER, selected = currentAction == ActionType.CYCLE_RINGER) {
                        persist(updateAction(kind, settings, ActionType.CYCLE_RINGER))
                    }
                    divider()
                    ActionOption(ActionType.TOGGLE_DND, selected = currentAction == ActionType.TOGGLE_DND) {
                        persist(updateAction(kind, settings, ActionType.TOGGLE_DND))
                    }
                    divider()
                    ActionOption(ActionType.CAMERA, selected = currentAction == ActionType.CAMERA) {
                        persist(updateAction(kind, settings, ActionType.CAMERA))
                    }
                    divider()
                    ActionOption(ActionType.FLASHLIGHT, selected = currentAction == ActionType.FLASHLIGHT) {
                        persist(updateAction(kind, settings, ActionType.FLASHLIGHT))
                    }
                    divider()
                    ActionOption(ActionType.SCREENSHOT, selected = currentAction == ActionType.SCREENSHOT) {
                        persist(updateAction(kind, settings, ActionType.SCREENSHOT))
                    }

                    CommonAction.entries.forEach { common ->
                        divider()
                        val selected = currentAction == ActionType.COMMON_FUNCTION && currentCustom.commonAction == common
                        ActionOption(common.title, Icons.Default.Tune, selected) {
                            val next = updateAction(kind, settings, ActionType.COMMON_FUNCTION)
                            persist(updateCustom(kind, next, currentCustom.copy(commonAction = common)))
                        }
                    }

                    divider()
                    ActionOption(ActionType.XIAOBU_SHORTCUT, selected = currentAction == ActionType.XIAOBU_SHORTCUT) {
                        persist(updateAction(kind, settings, ActionType.XIAOBU_SHORTCUT))
                    }
                    if (currentAction == ActionType.XIAOBU_SHORTCUT) {
                        CustomActionEditor(ActionType.XIAOBU_SHORTCUT, currentCustom) {
                            persist(updateCustom(kind, settings, it))
                        }
                    }

                    divider()
                    ActionOption(ActionType.CUSTOM_ACTIVITY, selected = currentAction == ActionType.CUSTOM_ACTIVITY) {
                        persist(updateAction(kind, settings, ActionType.CUSTOM_ACTIVITY))
                    }
                    if (currentAction == ActionType.CUSTOM_ACTIVITY) {
                        CustomActionEditor(ActionType.CUSTOM_ACTIVITY, currentCustom) {
                            persist(updateCustom(kind, settings, it))
                        }
                    }

                    divider()
                    ActionOption(ActionType.CUSTOM_URL, selected = currentAction == ActionType.CUSTOM_URL) {
                        persist(updateAction(kind, settings, ActionType.CUSTOM_URL))
                    }
                    if (currentAction == ActionType.CUSTOM_URL) {
                        CustomActionEditor(ActionType.CUSTOM_URL, currentCustom) {
                            persist(updateCustom(kind, settings, it))
                        }
                    }

                    divider()
                    ActionOption(ActionType.SHELL_COMMAND, selected = currentAction == ActionType.SHELL_COMMAND) {
                        if (currentAction != ActionType.SHELL_COMMAND) {
                            showShellPermissionDialog(context)
                        }
                        persist(updateAction(kind, settings, ActionType.SHELL_COMMAND))
                    }
                    if (currentAction == ActionType.SHELL_COMMAND) {
                        CustomActionEditor(ActionType.SHELL_COMMAND, currentCustom) {
                            persist(updateCustom(kind, settings, it))
                        }
                    }
                }
            }
          }
          item {
            InfoCard(if (kind == GestureKind.DOUBLE && settings.doubleAction == ActionType.NONE) "双击未配置，单击松开后会立即触发。" else if (kind == GestureKind.LONG) "长按触发后不会再触发单击或双击。" else "修改后会立即保存并由 Xposed 模块热加载。")
          }
        }
    }
}

@Composable
private fun FeedbackScreen(settings: AppSettings, persist: (AppSettings) -> Unit, back: () -> Unit, padding: PaddingValues) {
    val view = LocalView.current
    var vibrationValue by remember(settings.vibrationDurationMs) { mutableFloatStateOf(settings.vibrationDurationMs.toFloat()) }
    Column(Modifier.fillMaxSize().padding(padding)) {
        BackTitle("振动与提示", back)
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          item { SectionLabel("振动设置") }
          item {
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(14.dp)) {
                    SettingSwitchRow("触发时振动", settings.vibrationEnabled) { persist(settings.copy(vibrationEnabled = it)) }
                    Spacer(Modifier.height(4.dp))
                    Text("振动时长", style = MaterialTheme.typography.labelLarge, color = AppMuted)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Slider(
                            value = vibrationValue,
                            onValueChange = { vibrationValue = it },
                            onValueChangeFinished = { clickSound(view); persist(settings.copy(vibrationDurationMs = vibrationValue.toLong())) },
                            valueRange = 50f..1000f,
                            modifier = Modifier.weight(1f)
                        )
                        ValuePill("${vibrationValue.toLong()} ms")
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("50ms", style = MaterialTheme.typography.labelSmall, color = AppMuted)
                        Text("1000ms", style = MaterialTheme.typography.labelSmall, color = AppMuted)
                    }
                }
            }
          }
          item { SectionLabel("Toast 提示") }
          item {
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingSwitchRow("显示 Toast 提示", settings.toastEnabled) { persist(settings.copy(toastEnabled = it)) }
                    OutlinedTextField(
                        value = settings.toastText,
                        onValueChange = { persist(settings.copy(toastText = it)) },
                        label = { Text("提示内容") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
          }
          item { InfoCard("Toast 提示会在系统框架进程中显示，具体样式由当前 ROM 决定。") }
        }
    }
}

@Composable
private fun AdvancedScreen(settings: AppSettings, persist: (AppSettings) -> Unit, back: () -> Unit, padding: PaddingValues) {
    Column(Modifier.fillMaxSize().padding(padding)) {
        BackTitle("高级设置", back)
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          item { SectionLabel("系统行为") }
          item {
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(14.dp)) {
                    SettingSwitchRow("息屏时唤醒设备", settings.wakeScreenWhenOff) { persist(settings.copy(wakeScreenWhenOff = it)) }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Color(0xFFEAF0F7))
                    Text("按键事件来源", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Text("Oplus StrategyActionButtonKeyLaunchApp", style = MaterialTheme.typography.bodySmall, color = AppMuted)
                }
            }
          }
          item { SectionLabel("输入设备") }
          item {
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = settings.inputDevicePath,
                        onValueChange = { persist(settings.copy(inputDevicePath = it)) },
                        label = { Text("设备路径") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    NumberField(settings.keyCode.toString(), "按键码") {
                        it.toIntOrNull()?.let { code -> persist(settings.copy(keyCode = code)) }
                    }
                }
            }
          }
          item { InfoCard("默认路径为 /dev/input/event0，按键码为 735。模块会优先使用 Oplus 系统策略 hook。") }
        }
    }
}

@Composable
private fun SettingsScreen(navigate: (Route) -> Unit, padding: PaddingValues) {
    Column(Modifier.fillMaxSize().padding(padding)) {
        Text("设置", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp))
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          item { SectionLabel("应用信息") }
          item {
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Default.Tune)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("一加侧键自定义功能", fontWeight = FontWeight.SemiBold)
                        Text("com.slimenull.customsidebuttonfunctions", style = MaterialTheme.typography.bodySmall, color = AppMuted)
                        Text("版本 1.0.0", style = MaterialTheme.typography.bodySmall, color = AppMuted)
                    }
                }
            }
          }
          item { SettingRow(Icons.Default.Code, "开源许可", "Apache-2.0") { } }
          item { SettingRow(Icons.Default.Info, "关于我们", "项目与版本信息") { navigate(Route.About) } }
        }
    }
}

@Composable
private fun AboutScreen(navigate: (Route) -> Unit, padding: PaddingValues) {
    Column(Modifier.fillMaxSize().padding(padding)) {
        BackTitle("关于我们") { navigate(Route.Settings) }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
          item {
            Spacer(Modifier.height(34.dp))
            IconBadge(Icons.Default.Tune, size = 76.dp, iconSize = 38.dp)
            Spacer(Modifier.height(16.dp))
            Text("一加侧键自定义功能", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("让你的侧键，做更多的事", color = AppMuted, modifier = Modifier.padding(top = 6.dp))
            Text("com.slimenull.customsidebuttonfunctions", style = MaterialTheme.typography.bodySmall, color = AppMuted, modifier = Modifier.padding(top = 8.dp))
            Text("版本 1.0.0", style = MaterialTheme.typography.bodySmall, color = AppMuted)
          }
          item {
            Spacer(Modifier.height(22.dp))
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Text("开发者", style = MaterialTheme.typography.labelMedium, color = AppMuted)
                    Text("SlimeNull Issac", modifier = Modifier.padding(top = 4.dp))
                }
            }
          }
          item { SettingRow(Icons.Default.OpenInNew, "开源项目", "GitHub") { } }
        }
    }
}

@Composable
private fun BackTitle(title: String, back: () -> Unit) {
    val view = LocalView.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { clickSound(view); back() }) { Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = AppText) }
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val view = LocalView.current
    Card(
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth().clip(shape).clickable {
            clickSound(view)
            onClick()
        }
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = AppMuted)
            }
            Icon(Icons.Default.ArrowForward, contentDescription = null, tint = AppMuted, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun IntroCard(icon: ImageVector, title: String, description: String) {
    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, size = 48.dp, iconSize = 26.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = AppMuted, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun ActionOption(action: ActionType, selected: Boolean, onClick: () -> Unit) {
    ActionOption(action.title, actionIcon(action), selected, onClick)
}

@Composable
private fun ActionOption(title: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val view = LocalView.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable {
            clickSound(view)
            onClick()
        }.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(icon, size = 34.dp, iconSize = 19.dp)
        Spacer(Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Icon(
            imageVector = if (selected) Icons.Default.CheckCircle else Icons.Default.RemoveCircleOutline,
            contentDescription = if (selected) "已选择" else "未选择",
            tint = if (selected) AppBlue else Color(0xFFADC0D8),
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun CustomActionEditor(
    action: ActionType,
    custom: CustomActionSettings,
    onChange: (CustomActionSettings) -> Unit
) {
    when (action) {
        ActionType.COMMON_FUNCTION -> {
            ParameterCard("常用功能") {
                Text("已选择：${custom.commonAction.title}", color = AppMuted)
            }
        }
        ActionType.XIAOBU_SHORTCUT -> {
            ParameterCard("小布快捷指令") {
                OutlinedTextField(
                    value = custom.xiaobuShortcutId,
                    onValueChange = { onChange(custom.copy(xiaobuShortcutId = it)) },
                    label = { Text("快捷指令 ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        ActionType.CUSTOM_ACTIVITY -> {
            ParameterCard("Activity 参数") {
                OutlinedTextField(custom.activityPackage, { onChange(custom.copy(activityPackage = it)) }, label = { Text("包名") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(custom.activityClass, { onChange(custom.copy(activityClass = it)) }, label = { Text("Activity 类名") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(custom.activityAction, { onChange(custom.copy(activityAction = it)) }, label = { Text("Intent Action（可选）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        }
        ActionType.CUSTOM_URL -> {
            ParameterCard("Url Scheme 参数") {
                OutlinedTextField(
                    value = custom.urlScheme,
                    onValueChange = { onChange(custom.copy(urlScheme = it)) },
                    label = { Text("Url Scheme") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        ActionType.SHELL_COMMAND -> {
            ParameterCard("Shell 参数") {
                OutlinedTextField(
                    value = custom.shellCommand,
                    onValueChange = { onChange(custom.copy(shellCommand = it)) },
                    label = { Text("Shell 指令") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                val context = LocalContext.current
                OutlinedButton(onClick = { showShellPermissionDialog(context) }) {
                    Icon(Icons.Default.Security, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("请求 Root 权限")
                }
            }
        }
        else -> Unit
    }
}

@Composable
private fun ParameterCard(title: String, content: @Composable () -> Unit) {
    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun CommonActionSelector(value: CommonAction, onChange: (CommonAction) -> Unit) {
    val view = LocalView.current
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { clickSound(view); expanded = true }) { Text(value.title) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            CommonAction.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.title) },
                    onClick = { clickSound(view); onChange(option); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val view = LocalView.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.weight(1f))
        Switch(checked, { clickSound(view); onCheckedChange(it) })
    }
}

@Composable
private fun IconBadge(icon: ImageVector, size: androidx.compose.ui.unit.Dp = 40.dp, iconSize: androidx.compose.ui.unit.Dp = 21.dp) {
    Box(Modifier.size(size).background(AppBlueSoft, CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = AppBlueDark, modifier = Modifier.size(iconSize))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp, bottom = 2.dp))
}

@Composable
private fun InfoCard(text: String) {
    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF3FF))) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Info, contentDescription = null, tint = AppBlue)
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, color = AppBlueDark)
        }
    }
}

@Composable
private fun ValuePill(value: String) {
    Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF3F7FC))) {
        Text(value, modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge, color = AppText)
    }
}

@Composable
private fun NumberField(value: String, label: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )
}

private fun clickSound(view: View) {
    view.playSoundEffect(SoundEffectConstants.CLICK)
}

private fun showShellPermissionDialog(context: Context) {
    AlertDialog.Builder(context)
        .setTitle("执行 Shell 指令需要 Root")
        .setMessage(
            "由于系统限制，Shell 指令需要 Root 权限。\n\n" +
                "请确认 LSPosed 已勾选模块作用域“系统桌面 / com.android.launcher”，" +
                "然后点击下方按钮请求 Root。首次执行时请在 Root 管理器中允许授权。"
        )
        .setNegativeButton("稍后", null)
        .setPositiveButton("授予 Root 权限") { _, _ ->
            ShellRelayContract.requestRootPermission(context)
        }
        .show()
}

private fun selectedAction(kind: GestureKind, settings: AppSettings): ActionType = when (kind) {
    GestureKind.SINGLE -> settings.singleAction
    GestureKind.DOUBLE -> settings.doubleAction
    GestureKind.LONG -> settings.longAction
}

private fun selectedCustom(kind: GestureKind, settings: AppSettings): CustomActionSettings = when (kind) {
    GestureKind.SINGLE -> settings.singleCustom
    GestureKind.DOUBLE -> settings.doubleCustom
    GestureKind.LONG -> settings.longCustom
}

private fun updateAction(kind: GestureKind, settings: AppSettings, action: ActionType): AppSettings = when (kind) {
    GestureKind.SINGLE -> settings.copy(singleAction = action)
    GestureKind.DOUBLE -> settings.copy(doubleAction = action)
    GestureKind.LONG -> settings.copy(longAction = action)
}

private fun updateCustom(kind: GestureKind, settings: AppSettings, custom: CustomActionSettings): AppSettings = when (kind) {
    GestureKind.SINGLE -> settings.copy(singleCustom = custom)
    GestureKind.DOUBLE -> settings.copy(doubleCustom = custom)
    GestureKind.LONG -> settings.copy(longCustom = custom)
}

private fun gestureIcon(kind: GestureKind): ImageVector = when (kind) {
    GestureKind.SINGLE -> Icons.Default.Vibration
    GestureKind.DOUBLE -> Icons.Default.Vibration
    GestureKind.LONG -> Icons.Default.AccessTime
}

private fun actionIcon(action: ActionType): ImageVector = when (action) {
    ActionType.NONE -> Icons.Default.RemoveCircleOutline
    ActionType.CYCLE_RINGER -> Icons.Default.VolumeUp
    ActionType.TOGGLE_DND -> Icons.Default.NotificationsOff
    ActionType.CAMERA -> Icons.Default.CameraAlt
    ActionType.FLASHLIGHT -> Icons.Default.FlashOn
    ActionType.SCREENSHOT -> Icons.Default.CropFree
    ActionType.COMMON_FUNCTION -> Icons.Default.Tune
    ActionType.XIAOBU_SHORTCUT -> Icons.Default.Tune
    ActionType.CUSTOM_ACTIVITY -> Icons.Default.Code
    ActionType.CUSTOM_URL -> Icons.Default.OpenInNew
    ActionType.SHELL_COMMAND -> Icons.Default.Code
}
