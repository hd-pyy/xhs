package com.neoruaa.xhsdn

import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.kyant.capsule.ContinuousRoundedRectangle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.TopAppBarState
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import android.graphics.Color as AndroidColor

private const val PREFS_NAME = "XHSDownloaderPrefs"

data class SettingsUiState(
    val createLivePhotos: Boolean = true,
    val useCustomNaming: Boolean = false,
    val template: TextFieldValue = TextFieldValue(NamingFormat.DEFAULT_TEMPLATE),
    val tokens: List<NamingFormat.TokenDefinition> = emptyList(),
    val debugNotificationEnabled: Boolean = false,
    val selectiveDownload: Boolean = false,
    val keepScreenOn: Boolean = false,
    val showClipboardBubble: Boolean = true,
    val autoReadClipboard: Boolean = false,
    val manualInputLinks: Boolean = false
)

class SettingsViewModel(private val prefs: SharedPreferences) : ViewModel() {
    private val _state = MutableStateFlow(loadState())
    val state: StateFlow<SettingsUiState> = _state
    private var hasChanges = false

    private fun loadState(): SettingsUiState {
        val createLivePhotos = prefs.getBoolean("create_live_photos", true)
        val hasNewFlag = prefs.contains("use_custom_naming_format")
        val legacyFlag = prefs.getBoolean("use_metadata_file_names", false)
        val useCustomNaming = if (hasNewFlag) prefs.getBoolean("use_custom_naming_format", false) else legacyFlag
        var template = prefs.getString("custom_naming_template", NamingFormat.DEFAULT_TEMPLATE)
        if (template.isNullOrEmpty()) {
            template = NamingFormat.DEFAULT_TEMPLATE
        }
        val debugNotificationEnabled = prefs.getBoolean("debug_notification_enabled", false)
        val selectiveDownload = prefs.getBoolean("selective_download", false)
        val keepScreenOn = prefs.getBoolean("keep_screen_on", false)
        val showClipboardBubble = prefs.getBoolean("show_clipboard_bubble", true) // Default true
        val autoReadClipboard = prefs.getBoolean("auto_read_clipboard", false)
        val manualInputLinks = prefs.getBoolean("manual_input_links", false)
        return SettingsUiState(
            createLivePhotos = createLivePhotos,
            useCustomNaming = useCustomNaming,
            template = TextFieldValue(template),
            tokens = NamingFormat.getAvailableTokens(),
            debugNotificationEnabled = debugNotificationEnabled,
            selectiveDownload = selectiveDownload,
            keepScreenOn = keepScreenOn,
            showClipboardBubble = showClipboardBubble,
            autoReadClipboard = autoReadClipboard,
            manualInputLinks = manualInputLinks
        )
    }



    fun onCreateLivePhotosChange(enabled: Boolean) = updateState {
        it.copy(createLivePhotos = enabled).also { newState ->
            persist(newState)
        }
    }

    fun onUseCustomNamingChange(enabled: Boolean) = updateState {
        it.copy(useCustomNaming = enabled).also { newState ->
            persist(newState)
        }
    }

    fun onTemplateChange(value: TextFieldValue) = updateState {
        it.copy(template = value).also { newState ->
            persist(newState)
        }
    }

    fun onResetTemplate() = updateState {
        it.copy(template = TextFieldValue(NamingFormat.DEFAULT_TEMPLATE)).also { newState ->
            persist(newState)
        }
    }



    fun onDebugNotificationChange(enabled: Boolean) = updateState {
        it.copy(debugNotificationEnabled = enabled).also { newState ->
            persist(newState)
        }
    }

    fun onSelectiveDownloadChange(enabled: Boolean) = updateState {
        it.copy(selectiveDownload = enabled).also { newState ->
            persist(newState)
        }
    }

    fun onKeepScreenOnChange(enabled: Boolean) = updateState {
        it.copy(keepScreenOn = enabled).also { newState ->
            persist(newState)
        }
    }

    fun onShowClipboardBubbleChange(enabled: Boolean) = updateState {
        it.copy(showClipboardBubble = enabled).also { newState ->
            persist(newState)
        }
    }

    fun onAutoReadClipboardChange(enabled: Boolean) = updateState {
        it.copy(autoReadClipboard = enabled).also { newState ->
            persist(newState)
        }
    }

    fun onManualInputLinksChange(enabled: Boolean) = updateState {
        it.copy(manualInputLinks = enabled).also { newState ->
            persist(newState)
        }
    }

    private fun persist(state: SettingsUiState) {
        hasChanges = true
        prefs.edit()
            .putBoolean("create_live_photos", state.createLivePhotos)
            .putBoolean("use_custom_naming_format", state.useCustomNaming)
            .putString("custom_naming_template", state.template.text.ifBlank { NamingFormat.DEFAULT_TEMPLATE })
            .putBoolean("debug_notification_enabled", state.debugNotificationEnabled)
            .putBoolean("selective_download", state.selectiveDownload)
            .putBoolean("keep_screen_on", state.keepScreenOn)
            .putBoolean("show_clipboard_bubble", state.showClipboardBubble)
            .putBoolean("auto_read_clipboard", state.autoReadClipboard)
            .putBoolean("manual_input_links", state.manualInputLinks)
            .remove("use_metadata_file_names")
            .apply()
    }

    fun hasChanges(): Boolean = hasChanges

    private fun updateState(block: (SettingsUiState) -> SettingsUiState) {
        viewModelScope.launch {
            _state.emit(block(_state.value))
        }
    }
}

class SettingsViewModelFactory(private val prefs: SharedPreferences) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(prefs) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class SettingsActivity : ComponentActivity() {
    private val viewModel: SettingsViewModel by viewModels {
        SettingsViewModelFactory(getSharedPreferences(PREFS_NAME, MODE_PRIVATE))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                lightScrim = AndroidColor.TRANSPARENT,
                darkScrim = AndroidColor.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.auto(
                lightScrim = AndroidColor.TRANSPARENT,
                darkScrim = AndroidColor.TRANSPARENT
            )
        )
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = AndroidColor.TRANSPARENT
        window.navigationBarColor = AndroidColor.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.navigationBarDividerColor = AndroidColor.TRANSPARENT
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        val isNightMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightNavigationBars = !isNightMode
        setContent {
            val controller = ThemeController(ColorSchemeMode.System)
            val uiState by viewModel.state.collectAsStateWithLifecycle()
            val topBarState = rememberTopAppBarState()
            MiuixTheme(controller = controller) {
                SettingsScreen(
                    uiState = uiState,
                    onBack = { finishWithResult() },
                    onCreateLivePhotosChange = viewModel::onCreateLivePhotosChange,
                    onUseCustomNamingChange = viewModel::onUseCustomNamingChange,
                    onTemplateChange = viewModel::onTemplateChange,
                    onResetTemplate = viewModel::onResetTemplate,
                    onDebugNotificationChange = viewModel::onDebugNotificationChange,
                    onSelectiveDownloadChange = viewModel::onSelectiveDownloadChange,
                    onKeepScreenOnChange = viewModel::onKeepScreenOnChange,
                    onShowClipboardBubbleChange = viewModel::onShowClipboardBubbleChange,
                    onAutoReadClipboardChange = viewModel::onAutoReadClipboardChange,
                    onManualInputLinksChange = viewModel::onManualInputLinksChange,
                    topBarState = topBarState
                )
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        checkAccessibilityState()
    }
    
    private fun checkAccessibilityState() {
        // No-op
    }

    private fun finishWithResult() {
        setResult(if (viewModel.hasChanges()) RESULT_OK else RESULT_CANCELED)
        finish()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SettingsScreen(
    uiState: SettingsUiState,
    onBack: () -> Unit,
    onCreateLivePhotosChange: (Boolean) -> Unit,
    onUseCustomNamingChange: (Boolean) -> Unit,
    onTemplateChange: (TextFieldValue) -> Unit,
    onResetTemplate: () -> Unit,
    onDebugNotificationChange: (Boolean) -> Unit,
    onSelectiveDownloadChange: (Boolean) -> Unit,
    onKeepScreenOnChange: (Boolean) -> Unit,
    onShowClipboardBubbleChange: (Boolean) -> Unit,
    onAutoReadClipboardChange: (Boolean) -> Unit,
    onManualInputLinksChange: (Boolean) -> Unit,
    topBarState: TopAppBarState
) {
    val context = LocalContext.current
    val scrollBehavior = top.yukonga.miuix.kmp.basic.MiuixScrollBehavior(state = topBarState)

    top.yukonga.miuix.kmp.basic.Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets.statusBars
            .union(androidx.compose.foundation.layout.WindowInsets.displayCutout),
        topBar = {
            TopAppBar(
                title = stringResource(R.string.settings),
                navigationIcon = {
                    Icon(
                        imageVector = MiuixIcons.Back,
                        contentDescription = stringResource(R.string.back),
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .clickable { onBack() }
                    )
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .background(MiuixTheme.colorScheme.surface)
                .padding(padding),
            contentPadding = PaddingValues(bottom = 20.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
//            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Spacer(modifier = Modifier.size(12.dp))
            }

            item {
                SmallTitle(stringResource(R.string.download_options))
            }

            item {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp)
                ) {
                    MiuixSwitchWidget(
                        title = stringResource(R.string.create_live_photos),
                        description = stringResource(R.string.create_live_photos_desc),
                        checked = uiState.createLivePhotos,
                        onCheckedChange = onCreateLivePhotosChange
                    )

                    MiuixSwitchWidget(
                        title = stringResource(R.string.selective_download),
                        description = stringResource(R.string.selective_download_desc),
                        checked = uiState.selectiveDownload,
                        onCheckedChange = onSelectiveDownloadChange
                    )

                    MiuixSwitchWidget(
                        title = stringResource(R.string.debug_notifications),
                        description = stringResource(R.string.debug_notifications_desc),
                        checked = uiState.debugNotificationEnabled,
                        onCheckedChange = onDebugNotificationChange
                    )

                    MiuixSwitchWidget(
                        title = stringResource(R.string.keep_screen_on),
                        description = stringResource(R.string.keep_screen_on_desc),
                        checked = uiState.keepScreenOn,
                        onCheckedChange = onKeepScreenOnChange
                    )
                }
            }

            item {
                SmallTitle(stringResource(R.string.clipboard))
            }

            item {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp)
                ) {
                    MiuixSwitchWidget(
                        title = stringResource(R.string.manual_input_links),
                        description = stringResource(R.string.manual_input_links_desc),
                        checked = uiState.manualInputLinks,
                        onCheckedChange = onManualInputLinksChange
                    )

                    if (!uiState.manualInputLinks) {
                        MiuixSwitchWidget(
                            title = stringResource(R.string.show_clipboard_bubble),
                            description = stringResource(R.string.show_clipboard_bubble_desc),
                            checked = uiState.showClipboardBubble,
                            onCheckedChange = onShowClipboardBubbleChange
                        )

                        MiuixSwitchWidget(
                            title = stringResource(R.string.auto_read_clipboard),
                            description = stringResource(R.string.auto_read_clipboard_desc),
                            checked = uiState.autoReadClipboard,
                            onCheckedChange = onAutoReadClipboardChange
                        )
                    }
                }
            }

            item {
                SmallTitle(stringResource(R.string.file_naming))
            }

            item {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp)
                ) {
                    MiuixSwitchWidget(
                        title = stringResource(R.string.enable_custom_naming),
                        description = stringResource(R.string.enable_custom_naming_desc),
                        checked = uiState.useCustomNaming,
                        onCheckedChange = onUseCustomNamingChange
                    )

                    if (uiState.useCustomNaming) {
                        TextField(
                            value = uiState.template,
                            onValueChange = onTemplateChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            label = stringResource(R.string.naming_template),
                            enabled = uiState.useCustomNaming,
                            singleLine = false,
                            maxLines = 3,
                            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { }),
                            trailingIcon = {
                                if (uiState.useCustomNaming) {
                                    Icon(
                                        imageVector = MiuixIcons.Refresh,
                                        contentDescription = stringResource(R.string.reset_template),
                                        modifier = Modifier
                                            .padding(end = 16.dp)
                                            .size(20.dp)
                                            .clickable { onResetTemplate() }
                                    )
                                }
                            }
                        )

                        Text(
                            text = stringResource(R.string.insert_placeholder_hint),
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            fontSize = 14.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )

                        TokenGrid(
                            tokens = uiState.tokens,
                            enabled = uiState.useCustomNaming,
                            onInsert = { placeholder ->
                                val current = uiState.template
                                val selectionStart = current.selection.start.coerceAtLeast(0)
                                val selectionEnd = current.selection.end.coerceAtLeast(0)
                                val min = minOf(selectionStart, selectionEnd)
                                val max = maxOf(selectionStart, selectionEnd)
                                val newText = buildString {
                                    append(current.text.substring(0, min))
                                    append(placeholder)
                                    append(current.text.substring(max))
                                }
                                val newSelection = min + placeholder.length
                                onTemplateChange(
                                    TextFieldValue(
                                        text = newText,
                                        selection = androidx.compose.ui.text.TextRange(newSelection)
                                    )
                                )
                            }
                        )
                    }
                }
            }

            item {
                SmallTitle(stringResource(R.string.about))
            }

            item {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp)
                ) {
                    BasicComponent(
                        title = stringResource(R.string.version),
                        summary = stringResource(R.string.version_with_prefix, BuildConfig.VERSION_NAME),
                        onClick = { /* No action */ }
                    )
                    BasicComponent(
                        title = stringResource(R.string.visit_github),
                        titleColor = BasicComponentDefaults.titleColor(color = MiuixTheme.colorScheme.primary),
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/NEORUAA/XHS_Downloader_Android"))
                            context.startActivity(intent)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun MiuixSwitchWidget(
    title: String,
    description: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val toggleAction = {
        onCheckedChange(!checked)
    }

    BasicComponent(
        title = title,
        summary = description,
        onClick = toggleAction,
        endActions = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    )
}

@Composable
private fun TokenGrid(
    tokens: List<NamingFormat.TokenDefinition>,
    enabled: Boolean,
    onInsert: (String) -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(top = 0.dp, start = 16.dp, end = 12.dp, bottom = 16.dp)
    ) {
        tokens.forEach { token ->
            TokenChip(
                token = token,
                enabled = enabled,
                onInsert = onInsert,
                modifier = Modifier.fillMaxWidth(0.48f)
            )
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun TokenChip(
    token: NamingFormat.TokenDefinition,
    enabled: Boolean,
    onInsert: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = MiuixTheme.colorScheme.surface
    Card(
        modifier = modifier
            .border(width = 1.dp, color = borderColor, shape = ContinuousRoundedRectangle(16.dp))
            .then(if (enabled) Modifier.clickable { onInsert(token.placeholder) } else Modifier),
        cornerRadius = 14.dp,
        colors = CardDefaults.defaultColors(
            color = if (enabled) MiuixTheme.colorScheme.surfaceVariant else MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(token.labelResId),
                fontSize = 16.sp,
                color = if (enabled) MiuixTheme.colorScheme.onSurface else MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            Text(
                text = token.placeholder,
                fontSize = 14.sp,
                color = if (enabled) MiuixTheme.colorScheme.onSurfaceVariantSummary else MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.5f)
            )
        }
    }
}
