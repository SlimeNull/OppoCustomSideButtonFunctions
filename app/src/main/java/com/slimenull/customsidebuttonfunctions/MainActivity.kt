package com.slimenull.customsidebuttonfunctions

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.RoundedCorner
import android.view.SoundEffectConstants
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowInsets
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.slimenull.customsidebuttonfunctions.data.SettingsStore
import com.slimenull.customsidebuttonfunctions.model.ActionType
import com.slimenull.customsidebuttonfunctions.model.AppSettings
import com.slimenull.customsidebuttonfunctions.model.CommonAction
import com.slimenull.customsidebuttonfunctions.model.CustomActionSettings
import com.slimenull.customsidebuttonfunctions.model.CursorControlMode
import com.slimenull.customsidebuttonfunctions.model.MorseBinding
import com.slimenull.customsidebuttonfunctions.model.OperationMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect

private val AppBackground = Color(0xFFF3F7FD)
private val AppBlue = Color(0xFF347FE8)
private val AppBlueDark = Color(0xFF1954A6)
private val AppBlueSoft = Color(0xFFE7F1FF)
private val AppText = Color(0xFF152238)
private val AppMuted = Color(0xFF718198)
private val AppGreen = Color(0xFF2DAE78)
private const val PageShadeOpacity = 0.24f
private const val PageSlideFraction = 0.25f
private const val PageTransitionDurationMs = 350
private val AppColors = lightColorScheme(
    primary = AppBlue,
    onPrimary = Color.White,
    primaryContainer = AppBlueSoft,
    onPrimaryContainer = AppBlueDark,
    inversePrimary = Color(0xFFB3D1FF),
    secondary = Color(0xFF466A80),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0EEF4),
    onSecondaryContainer = Color(0xFF254C61),
    tertiary = Color(0xFF28765D),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDDF3E9),
    onTertiaryContainer = Color(0xFF17513D),
    background = AppBackground,
    onBackground = AppText,
    surface = Color.White,
    onSurface = AppText,
    surfaceVariant = Color(0xFFE9F0F8),
    onSurfaceVariant = AppMuted,
    surfaceTint = AppBlue,
    surfaceBright = Color.White,
    surfaceDim = Color(0xFFE8EEF5),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFCFDFF),
    surfaceContainer = Color(0xFFF8FBFF),
    surfaceContainerHigh = Color(0xFFF1F5FA),
    surfaceContainerHighest = Color(0xFFE8F0F7),
    inverseSurface = AppText,
    inverseOnSurface = Color.White,
    outline = Color(0xFF889AAF),
    outlineVariant = Color(0xFFD6E1EC),
    error = Color(0xFFB43F43),
    onError = Color.White,
    errorContainer = Color(0xFFFDE8E8),
    onErrorContainer = Color(0xFF6D1C22),
    scrim = Color.Black
)

private enum class BottomTab { HOME, OTHER, ABOUT }

private enum class GestureKind(val title: String, val subtitle: String) {
    SINGLE("单击", "按下并松开后立即触发"),
    DOUBLE("双击", "连续两次按下并松开"),
    LONG("长按", "按住并达到指定时间")
}

private sealed interface Route {
    data object Home : Route
    data object Other : Route
    data object About : Route
    data object Morse : Route
    data object Feedback : Route
    data object Advanced : Route
    data class Gesture(val kind: GestureKind) : Route
}

private data class ActionChoice(val action: ActionType, val common: CommonAction? = null) {
    val title: String get() = common?.title ?: action.title
    val icon: ImageVector get() = if (common != null) Icons.Default.Tune else actionIcon(action)
}

private val actionChoices = ActionType.entries.flatMap { action ->
    if (action == ActionType.COMMON_FUNCTION) {
        CommonAction.entries.map { ActionChoice(action, it) }
    } else {
        listOf(ActionChoice(action))
    }
}
private val morseActionChoices = actionChoices.filter { it.action != ActionType.NONE }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { CustomSideButtonApp() }
    }
}

@Composable
fun rememberScreenCornerRadius(): Dp {
    val view = LocalView.current
    var radiusPx by remember(view) { mutableIntStateOf(0) }

    DisposableEffect(view) {
        val observer = view.viewTreeObserver
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            radiusPx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                view.rootWindowInsets
                    ?.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)
                    ?.radius ?: 0
            } else {
                0
            }
        }

        observer.addOnGlobalLayoutListener(listener)
        listener.onGlobalLayout()
        onDispose {
            if (observer.isAlive) observer.removeOnGlobalLayoutListener(listener)
        }
    }

    return with(LocalDensity.current) { radiusPx.toDp() }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
private fun CustomSideButtonApp() {
    val context = LocalContext.current
    val view = LocalView.current
    var settings by remember { mutableStateOf(SettingsStore.load(context)) }
    var route by remember { mutableStateOf<Route>(Route.Home) }
    var displayedDetail by remember { mutableStateOf<Route?>(null) }
    var predictiveTarget by remember { mutableStateOf<Route?>(null) }
    val predictiveProgress = remember { Animatable(0f) }
    var routeTransitionEpoch by remember { mutableIntStateOf(0) }
    var skipCommittedReturn by remember { mutableStateOf(false) }
    var contentWidth by remember { mutableFloatStateOf(0f) }

    fun persist(next: AppSettings) {
        settings = next
        SettingsStore.save(context, next)
    }

    fun navigate(next: Route) {
        if (route != next) {
            skipCommittedReturn = false
            if (!isPrimaryRoute(next)) displayedDetail = next
            route = next
        }
    }

    val primaryRoute: Route = when (route) {
        Route.Other -> Route.Other
        Route.About -> Route.About
        else -> Route.Home
    }

    val animatedShade by animateFloatAsState(
        targetValue = if (isPrimaryRoute(route)) 0f else PageShadeOpacity,
        animationSpec = tween(PageTransitionDurationMs),
        label = "Page shade"
    )
    val pageShade = when {
        predictiveTarget != null -> animatedShade * (1f - predictiveProgress.value)
        skipCommittedReturn -> 0f
        else -> animatedShade
    }
    val animatedRootOffset by animateFloatAsState(
        targetValue = if (isPrimaryRoute(route)) 0f else -PageSlideFraction,
        animationSpec = tween(PageTransitionDurationMs),
        label = "Primary page offset"
    )
    val rootOffset = when {
        predictiveTarget != null -> -PageSlideFraction * (1f - predictiveProgress.value)
        skipCommittedReturn -> 0f
        else -> animatedRootOffset
    }
    val cornerDp = rememberScreenCornerRadius()

    PredictiveBackHandler(enabled = !isPrimaryRoute(route)) { progress ->
        val destination = backDestination()
        try {
            predictiveTarget = destination
            progress.collect { event ->
                predictiveProgress.snapTo(event.progress.coerceIn(0f, 1f))
            }
            predictiveProgress.animateTo(1f, tween(PageTransitionDurationMs))
            // Replace the transition host at commit so it cannot retain the outgoing page.
            routeTransitionEpoch++
            skipCommittedReturn = true
            route = destination
            predictiveTarget = null
        } catch (_: CancellationException) {
            predictiveProgress.animateTo(0f, tween(180))
        } finally {
            predictiveTarget = null
            predictiveProgress.snapTo(0f)
        }
    }

    MaterialTheme(colorScheme = AppColors) {
        Scaffold(
            containerColor = AppBackground
        ) { padding ->
            Box(Modifier.fillMaxSize().onSizeChanged { contentWidth = it.width.toFloat() }) {
                PrimaryPageLayer(
                    primaryRoute = primaryRoute,
                    settings = settings,
                    navigate = ::navigate,
                    persist = ::persist,
                    padding = padding,
                    contentWidth = contentWidth,
                    rootOffset = rootOffset,
                    onTabSelected = { tab ->
                        clickSound(view)
                        navigate(
                            when (tab) {
                                BottomTab.HOME -> Route.Home
                                BottomTab.OTHER -> Route.Other
                                BottomTab.ABOUT -> Route.About
                            }
                        )
                    }
                )

                Box(Modifier.fillMaxSize()
                    .background(Color.Black.copy(alpha = pageShade))
                    .then(if (!isPrimaryRoute(route) || pageShade > 0f) Modifier.pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) awaitPointerEvent().changes.forEach { it.consume() }
                        }
                    } else Modifier)
                )

                key(routeTransitionEpoch) {
                    AnimatedVisibility(
                        visible = !isPrimaryRoute(route),
                        modifier = Modifier.fillMaxSize().graphicsLayer {
                            val progress = if (predictiveTarget != null) predictiveProgress.value else 0f
                            translationX = contentWidth * progress
                            scaleX = 1f - 0.2f * progress
                            scaleY = 1f - 0.2f * progress
                        },
                        enter = slideInHorizontally(tween(PageTransitionDurationMs)) { it } +
                            scaleIn(initialScale = 0.8f, animationSpec = tween(PageTransitionDurationMs)),
                        exit = slideOutHorizontally(tween(PageTransitionDurationMs)) { it } +
                            scaleOut(targetScale = 0.8f, animationSpec = tween(PageTransitionDurationMs)),
                        label = "Detail transition"
                    ) {
                        displayedDetail?.let { detail ->
                            val clipDetail = predictiveTarget != null ||
                                transition.currentState != transition.targetState
                            Box(Modifier.fillMaxSize().graphicsLayer {
                                shape = RoundedCornerShape(cornerDp)
                                clip = clipDetail
                            }.background(AppBackground)) {
                                RouteScreen(
                                    route = detail,
                                    settings = settings,
                                    navigate = ::navigate,
                                    persist = ::persist,
                                    padding = padding
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun PrimaryPageLayer(
    primaryRoute: Route,
    settings: AppSettings,
    navigate: (Route) -> Unit,
    persist: (AppSettings) -> Unit,
    padding: PaddingValues,
    contentWidth: Float,
    rootOffset: Float,
    onTabSelected: (BottomTab) -> Unit
) {
    val selectedTab = when (primaryRoute) {
        Route.Other -> BottomTab.OTHER
        Route.About -> BottomTab.ABOUT
        else -> BottomTab.HOME
    }
    Column(
        Modifier.fillMaxSize()
            .graphicsLayer { translationX = contentWidth * rootOffset }
            .background(AppBackground)
    ) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            AnimatedContent(
                targetState = primaryRoute,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    val direction = routeDirection(initialState, targetState)
                    (slideInHorizontally(tween(PageTransitionDurationMs)) { it * direction } togetherWith
                        slideOutHorizontally(tween(PageTransitionDurationMs)) { -it * direction })
                        .using(SizeTransform(clip = true))
                },
                label = "Tab transition"
            ) { current ->
                RouteScreen(
                    route = current,
                    settings = settings,
                    navigate = navigate,
                    persist = persist,
                    padding = padding
                )
            }
        }
        NavigationBar(containerColor = Color.White) {
            NavigationBarItem(
                selected = selectedTab == BottomTab.HOME,
                onClick = { onTabSelected(BottomTab.HOME) },
                icon = { Icon(Icons.Default.Home, contentDescription = "首页") },
                label = { Text("首页") }
            )
            NavigationBarItem(
                selected = selectedTab == BottomTab.OTHER,
                onClick = { onTabSelected(BottomTab.OTHER) },
                icon = { Icon(Icons.Default.MoreHoriz, contentDescription = "其他") },
                label = { Text("其他") }
            )
            NavigationBarItem(
                selected = selectedTab == BottomTab.ABOUT,
                onClick = { onTabSelected(BottomTab.ABOUT) },
                icon = { Icon(Icons.Default.Info, contentDescription = "关于") },
                label = { Text("关于") }
            )
        }
    }
}

private fun backDestination(): Route = Route.Home

private fun isPrimaryRoute(route: Route): Boolean =
    route is Route.Home || route is Route.Other || route is Route.About

private fun routeDirection(from: Route, to: Route): Int =
    if (primaryRouteIndex(to) >= primaryRouteIndex(from)) 1 else -1

private fun primaryRouteIndex(route: Route): Int = when (route) {
    Route.Home -> 0
    Route.Other -> 1
    Route.About -> 2
    else -> 0
}

@Composable
private fun RouteScreen(
    route: Route,
    settings: AppSettings,
    navigate: (Route) -> Unit,
    persist: (AppSettings) -> Unit,
    padding: PaddingValues
) {
    when (route) {
        Route.Home -> HomeScreen(settings, navigate, persist, padding)
        Route.Other -> OtherScreen(settings, persist, padding)
        Route.About -> AboutScreen(padding)
        Route.Morse -> MorseScreen(settings, persist, { navigate(Route.Home) }, padding)
        Route.Feedback -> FeedbackScreen(settings, persist, { navigate(Route.Home) }, padding)
        Route.Advanced -> AdvancedScreen(settings, persist, { navigate(Route.Home) }, padding)
        is Route.Gesture -> GestureScreen(route.kind, settings, persist, { navigate(Route.Home) }, padding)
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun HomeScreen(
    settings: AppSettings,
    navigate: (Route) -> Unit,
    persist: (AppSettings) -> Unit,
    padding: PaddingValues
) {
    val view = LocalView.current
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
            item { SectionLabel("操作模式") }
            item {
                val modeColors = SegmentedButtonDefaults.colors(
                    activeContainerColor = AppBlueSoft,
                    activeContentColor = AppBlueDark,
                    activeBorderColor = AppBlue,
                    inactiveContainerColor = Color.White,
                    inactiveContentColor = AppText,
                    inactiveBorderColor = Color(0xFF9AAFC6)
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    OperationMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = settings.operationMode == mode,
                            onClick = { clickSound(view); persist(settings.copy(operationMode = mode)) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index, OperationMode.entries.size, baseShape = RoundedCornerShape(8.dp)
                            ),
                            colors = modeColors,
                            modifier = Modifier.weight(if (index == 0) 2f else 3f),
                            label = { Text(mode.title) }
                        )
                    }
                }
            }
            item {
                AnimatedContent(
                    targetState = settings.operationMode,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(PageTransitionDurationMs)) +
                            scaleIn(initialScale = 0.9f, animationSpec = tween(PageTransitionDurationMs)))
                            .togetherWith(
                                fadeOut(animationSpec = tween(PageTransitionDurationMs)) +
                                    scaleOut(targetScale = 0.9f, animationSpec = tween(PageTransitionDurationMs))
                            )
                            .using(
                                SizeTransform(
                                    clip = false,
                                    sizeAnimationSpec = { _, _ -> tween(PageTransitionDurationMs) }
                                )
                            )
                    },
                    label = "Operation mode content"
                ) { mode ->
                    Column(
                        Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SectionLabel(mode.title)
                        when (mode) {
                            OperationMode.DISABLED -> Unit
                            OperationMode.SIMPLE -> {
                                GestureRow(GestureKind.SINGLE, settings.singleAction, settings.singleCustom, navigate)
                                GestureRow(GestureKind.DOUBLE, settings.doubleAction, settings.doubleCustom, navigate)
                                GestureRow(GestureKind.LONG, settings.longAction, settings.longCustom, navigate)
                            }
                            OperationMode.MORSE -> {
                                SettingRow(Icons.Default.Code, "摩斯电码设置", "${settings.morseBindings.size} 条指令") {
                                    navigate(Route.Morse)
                                }
                            }
                        }
                    }
                }
            }
            item { SectionLabel("其他设置") }
            item {
                SettingRow(Icons.Default.Vibration, "振动与提示", "配置触发振动和 Toast 提示") { navigate(Route.Feedback) }
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
    var sliderValue by remember(kind, settings) {
        mutableFloatStateOf(
            when (kind) {
                GestureKind.DOUBLE -> settings.doubleClickWindowMs.coerceIn(100L, 800L).toFloat()
                GestureKind.LONG -> settings.longPressMs.coerceIn(100L, 800L).toFloat()
                GestureKind.SINGLE -> 0f
            }
        )
    }
    val currentAction = selectedAction(kind, settings)
    val currentCustom = selectedCustom(kind, settings)
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
                                valueRange = 100f..800f,
                                modifier = Modifier.weight(1f)
                            )
                            ValuePill("${sliderValue.toLong()} ms")
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("100ms", style = MaterialTheme.typography.labelSmall, color = AppMuted)
                            Text("800ms", style = MaterialTheme.typography.labelSmall, color = AppMuted)
                        }
                    }
                }
            }
          }
          item { SectionLabel("选择要执行的功能") }
          item {
            val selected = ActionChoice(
                currentAction,
                if (currentAction == ActionType.COMMON_FUNCTION) currentCustom.commonAction else null
            )
            ActionPicker(selected) { choice ->
                val next = updateAction(kind, settings, choice.action)
                persist(if (choice.common != null) {
                    updateCustom(kind, next, currentCustom.copy(commonAction = choice.common))
                } else next)
            }
          }
          if (currentAction in listOf(ActionType.XIAOBU_SHORTCUT, ActionType.CUSTOM_ACTIVITY,
                  ActionType.CUSTOM_URL, ActionType.SHELL_COMMAND)) {
            item {
                CustomActionEditor(currentAction, currentCustom) {
                    persist(updateCustom(kind, settings, it))
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
private fun MorseScreen(
    settings: AppSettings,
    persist: (AppSettings) -> Unit,
    back: () -> Unit,
    padding: PaddingValues
) {
    val view = LocalView.current
    var editorOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<MorseBinding?>(null) }
    var deleting by remember { mutableStateOf<MorseBinding?>(null) }

    Column(Modifier.fillMaxSize().padding(padding)) {
        BackTitle("摩斯电码操作", back)
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    SectionLabel("指令映射")
                    Button(onClick = { clickSound(view); editing = null; editorOpen = true }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("添加")
                    }
                }
            }
            if (settings.morseBindings.isEmpty()) {
                item { Text("暂无指令", color = AppMuted, modifier = Modifier.padding(vertical = 16.dp)) }
            }
            items(settings.morseBindings, key = { it.sequence }) { binding ->
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).clickable {
                            clickSound(view)
                            editing = binding
                            editorOpen = true
                        }) {
                            Text(morseSequenceTitle(binding.sequence),
                                fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(morseActionTitle(binding), color = AppMuted,
                                style = MaterialTheme.typography.bodySmall, maxLines = 2,
                                overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { clickSound(view); editing = binding; editorOpen = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "编辑指令")
                        }
                        IconButton(onClick = { clickSound(view); deleting = binding }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "删除指令")
                        }
                    }
                }
            }
            item { SectionLabel("按键设置") }
            item {
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(14.dp)) {
                        SettingSwitchRow("按下时振动", settings.morsePressVibrationEnabled) {
                            persist(settings.copy(morsePressVibrationEnabled = it))
                        }
                        HorizontalDivider(color = Color(0xFFEAF0F7))
                        SettingSwitchRow("持续按到长按时间时振动", settings.morseLongVibrationEnabled) {
                            persist(settings.copy(morseLongVibrationEnabled = it))
                        }
                        HorizontalDivider(color = Color(0xFFEAF0F7))
                        SettingSwitchRow("尽可能立即执行操作", settings.morseImmediateExecutionEnabled) {
                            persist(settings.copy(morseImmediateExecutionEnabled = it))
                        }
                    }
                }
            }
            item { SectionLabel("判定时间") }
            item {
                MorseTimingCard("长按持续时间", settings.morseLongPressMs) {
                    persist(settings.copy(morseLongPressMs = it))
                }
            }
            item {
                MorseTimingCard("指令判断等待时间", settings.morseCommandWindowMs) {
                    persist(settings.copy(morseCommandWindowMs = it))
                }
            }
        }
    }

    if (editorOpen) {
        MorseBindingEditorDialog(
            original = editing,
            existing = settings.morseBindings,
            onDismiss = { editorOpen = false },
            onSave = { binding ->
                val index = settings.morseBindings.indexOfFirst { it.sequence == editing?.sequence }
                val next = settings.morseBindings.toMutableList().apply {
                    if (index >= 0) set(index, binding) else add(binding)
                }
                persist(settings.copy(morseBindings = next))
                editorOpen = false
            }
        )
    }
    deleting?.let { binding ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除指令 ${binding.sequence}？") },
            text = { Text(morseActionTitle(binding)) },
            confirmButton = {
                TextButton(onClick = {
                    persist(settings.copy(morseBindings = settings.morseBindings.filterNot { it.sequence == binding.sequence }))
                    deleting = null
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun MorseTimingCard(title: String, value: Long, onChange: (Long) -> Unit) {
    val view = LocalView.current
    var sliderValue by remember(value) {
        mutableFloatStateOf(value.coerceIn(100L, 800L).toFloat())
    }
    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    onValueChangeFinished = { clickSound(view); onChange(sliderValue.toLong()) },
                    valueRange = 100f..800f,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    Modifier.width(82.dp).height(38.dp)
                        .background(Color(0xFFF3F7FC), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("${sliderValue.toLong()} ms", style = MaterialTheme.typography.labelMedium)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("100ms", style = MaterialTheme.typography.labelSmall, color = AppMuted)
                Text("800ms", style = MaterialTheme.typography.labelSmall, color = AppMuted)
            }
        }
    }
}

private fun morseActionTitle(binding: MorseBinding): String =
    if (binding.action == ActionType.COMMON_FUNCTION) binding.custom.commonAction.title else binding.action.title

private fun morseSequenceTitle(sequence: String): String =
    sequence.map { if (it == '0') "短" else "长" }.joinToString("")

@Composable
private fun MorseBindingEditorDialog(
    original: MorseBinding?,
    existing: List<MorseBinding>,
    onDismiss: () -> Unit,
    onSave: (MorseBinding) -> Unit
) {
    var sequence by remember(original) { mutableStateOf(original?.sequence.orEmpty()) }
    var action by remember(original) { mutableStateOf(original?.action ?: ActionType.SCREENSHOT) }
    var custom by remember(original) { mutableStateOf(original?.custom ?: CustomActionSettings()) }
    var showError by remember(original) { mutableStateOf(false) }
    val error = when {
        sequence.isEmpty() -> "请输入指令序列"
        existing.any { it.sequence == sequence && it.sequence != original?.sequence } -> "该序列已存在"
        else -> null
    }
    val selected = ActionChoice(action, if (action == ActionType.COMMON_FUNCTION) custom.commonAction else null)
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.85f
    val view = LocalView.current

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = AppBackground,
            modifier = Modifier.fillMaxWidth().heightIn(max = maxHeight)
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (original == null) "添加指令" else "编辑指令",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = sequence,
                    onValueChange = { sequence = it.filter { symbol -> symbol == '0' || symbol == '1' } },
                    label = { Text("指令序列（0 短、1 长）") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = showError && error != null,
                    supportingText = if (showError && error != null) { { Text(error) } } else null,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { clickSound(view); sequence += '0' }, modifier = Modifier.weight(1f)) {
                        Text("短 0")
                    }
                    OutlinedButton(onClick = { clickSound(view); sequence += '1' }, modifier = Modifier.weight(1f)) {
                        Text("长 1")
                    }
                    IconButton(onClick = { clickSound(view); if (sequence.isNotEmpty()) sequence = sequence.dropLast(1) }) {
                        Icon(Icons.Default.Backspace, contentDescription = "删除末位")
                    }
                }
                SectionLabel("执行动作")
                ActionPicker(selected, morseActionChoices) { choice ->
                    action = choice.action
                    if (choice.common != null) custom = custom.copy(commonAction = choice.common)
                }
                if (action in listOf(ActionType.XIAOBU_SHORTCUT, ActionType.CUSTOM_ACTIVITY,
                        ActionType.CUSTOM_URL, ActionType.SHELL_COMMAND)) {
                    CustomActionEditor(action, custom) { custom = it }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Button(onClick = {
                        showError = true
                        if (error == null) onSave(MorseBinding(sequence, action, custom))
                    }) { Text("保存") }
                }
            }
        }
    }
}

@Composable
private fun FeedbackScreen(settings: AppSettings, persist: (AppSettings) -> Unit, back: () -> Unit, padding: PaddingValues) {
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
          item { SectionLabel("未知摩斯电码序列提示") }
          item {
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingSwitchRow("Toast 与双震动", settings.unknownMorseFeedbackEnabled) {
                        persist(settings.copy(unknownMorseFeedbackEnabled = it))
                    }
                    OutlinedTextField(
                        value = settings.unknownMorseToastText,
                        onValueChange = { persist(settings.copy(unknownMorseToastText = it)) },
                        label = { Text("提示内容") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
          }
        }
    }
}

@Composable
private fun OtherScreen(settings: AppSettings, persist: (AppSettings) -> Unit, padding: PaddingValues) {
    val view = LocalView.current
    Column(Modifier.fillMaxSize().padding(padding)) {
        Text(
            "其他",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { SectionLabel("输入法光标") }
            item {
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("音量键控制光标", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        val cursorColors = SegmentedButtonDefaults.colors(
                            activeContainerColor = AppBlueSoft,
                            activeContentColor = AppBlueDark,
                            activeBorderColor = AppBlue,
                            inactiveContainerColor = Color.White,
                            inactiveContentColor = AppText,
                            inactiveBorderColor = Color(0xFF9AAFC6)
                        )
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            CursorControlMode.entries.forEachIndexed { index, mode ->
                                SegmentedButton(
                                    selected = settings.cursorControlMode == mode,
                                    onClick = { clickSound(view); persist(settings.copy(cursorControlMode = mode)) },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index, CursorControlMode.entries.size, baseShape = RoundedCornerShape(8.dp)
                                    ),
                                    colors = cursorColors,
                                    modifier = Modifier.weight(
                                        when (index) {
                                            0 -> 2f
                                            else -> 3f
                                        }
                                    ),
                                    label = {
                                        Text(
                                            mode.title,
                                            maxLines = 2,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
            item {
                InfoCard("设备亮屏、未锁屏、未通话且输入法窗口可见时接管音量键；隐藏输入法后恢复普通音量调节。")
            }
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
private fun AboutScreen(padding: PaddingValues) {
    val context = LocalContext.current
    val view = LocalView.current
    Column(Modifier.fillMaxSize().padding(padding)) {
        Text("关于", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp))
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
           item {
             Row(
                 Modifier.fillMaxWidth(),
                 horizontalArrangement = Arrangement.spacedBy(10.dp)
             ) {
                 OutlinedButton(
                     onClick = {
                         clickSound(view)
                         openExternalUrl(context, "https://www.gnu.org/licenses/lgpl-3.0.html")
                     },
                     modifier = Modifier.weight(1f)
                 ) {
                     Icon(Icons.Default.Code, contentDescription = null)
                     Spacer(Modifier.width(6.dp))
                     Text("许可证")
                 }
                 OutlinedButton(
                     onClick = {
                         clickSound(view)
                         openExternalUrl(context, "https://github.com/SlimeNull/OppoCustomSideButtonFunctions")
                     },
                     modifier = Modifier.weight(1f)
                 ) {
                     Icon(Icons.Default.OpenInNew, contentDescription = null)
                     Spacer(Modifier.width(6.dp))
                     Text("仓库")
                 }
             }
           }
           item {
             Text(
                 "GNU Lesser General Public License v3.0",
                 style = MaterialTheme.typography.bodySmall,
                 color = AppMuted,
                 modifier = Modifier.fillMaxWidth(),
                 textAlign = androidx.compose.ui.text.style.TextAlign.Center
             )
           }
        }
    }
}

private fun openExternalUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addCategory(Intent.CATEGORY_BROWSABLE)
        )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionPicker(
    selected: ActionChoice,
    choices: List<ActionChoice> = actionChoices,
    onSelect: (ActionChoice) -> Unit
) {
    val view = LocalView.current
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { clickSound(view); expanded = it },
        modifier = Modifier.fillMaxWidth()
    ) {
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconBadge(selected.icon, size = 34.dp, iconSize = 19.dp)
                Spacer(Modifier.width(12.dp))
                Text(selected.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Icon(Icons.Default.ExpandMore, contentDescription = "选择功能", tint = AppMuted)
            }
        }
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(14.dp)
        ) {
            choices.forEach { choice ->
                DropdownMenuItem(
                    text = { Text(choice.title) },
                    leadingIcon = { Icon(choice.icon, contentDescription = null) },
                    trailingIcon = if (choice == selected) {
                        { Icon(Icons.Default.CheckCircle, contentDescription = "已选择", tint = AppBlue) }
                    } else null,
                    onClick = {
                        clickSound(view)
                        expanded = false
                        onSelect(choice)
                    }
                )
            }
        }
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
