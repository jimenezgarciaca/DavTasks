package org.tasks.compose.edit

import org.tasks.themes.TasksIcons
import org.tasks.compose.components.SymbolIcon
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import org.tasks.themes.TestTags
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.IntentCompat.getParcelableExtra
import androidx.fragment.compose.AndroidFragment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.todoroo.andlib.utility.AndroidUtilities.atLeastOreoMR1
import com.todoroo.astrid.files.FilesControlSet
import com.todoroo.astrid.repeats.RepeatControlSet
import com.todoroo.astrid.tags.TagsControlSet
import com.todoroo.astrid.timers.TimerControlSet
import com.todoroo.astrid.ui.ReminderControlSet
import com.todoroo.astrid.ui.StartDateControlSet
import kotlinx.coroutines.launch
import org.tasks.R
import org.tasks.compose.FilterSelectionActivity.Companion.EXTRA_FILTER
import org.tasks.compose.FilterSelectionActivity.Companion.launch
import org.tasks.compose.pickers.DueDatePickerSheet
import org.tasks.compose.pickers.dueDateFromSelection
import org.tasks.compose.pickers.dueDateToSelection
import org.tasks.data.entity.Alarm
import org.tasks.data.entity.Task
import org.tasks.data.entity.TaskAttachment
import org.tasks.data.entity.UserActivity
import org.tasks.dialogs.Linkify
import org.tasks.extensions.Context.findActivity
import org.tasks.extensions.Context.is24HourFormat
import org.tasks.extensions.hideKeyboardThen
import org.tasks.files.FileHelper
import org.tasks.filters.CaldavFilter
import org.tasks.fragments.CommentBarFragment
import org.tasks.kmp.org.tasks.extensions.gesturesDisabled
import org.tasks.kmp.org.tasks.taskedit.TaskEditViewState
import org.tasks.markdown.MarkdownProvider
import org.tasks.preferences.Preferences
import org.tasks.themes.TasksTheme
import org.tasks.ui.CalendarControlSet
import org.tasks.ui.LocationControlSet
import org.tasks.ui.SubtaskControlSet
import org.tasks.ui.TaskEditViewModel
import org.tasks.ui.TaskEditViewModel.Companion.TAG_CREATION
import org.tasks.ui.TaskEditViewModel.Companion.TAG_DESCRIPTION
import org.tasks.ui.TaskEditViewModel.Companion.TAG_DUE_DATE
import org.tasks.ui.TaskEditViewModel.Companion.TAG_LIST
import org.tasks.ui.TaskEditViewModel.Companion.TAG_PRIORITY
import org.tasks.ui.TaskEditViewModel.Companion.TAG_TITLE
import org.tasks.utility.copyToClipboard
import tasks.kmp.generated.resources.Res
import tasks.kmp.generated.resources.delete_task
import tasks.kmp.generated.resources.menu_discard_changes
import tasks.kmp.generated.resources.more_options
import tasks.kmp.generated.resources.new_task
import timber.log.Timber
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditScreen(
    editViewModel: TaskEditViewModel,
    viewState: TaskEditViewState,
    comments: List<UserActivity>,
    save: () -> Unit,
    discard: () -> Unit,
    delete: () -> Unit,
    deleteComment: (UserActivity) -> Unit,
    onClickRepeat: () -> Unit,
    repeatRuleToString: (String?) -> String?,
    markdownProvider: MarkdownProvider,
    linkify: Linkify?,
    locale: Locale,
    preferences: Preferences,
    colorProvider: (Int) -> Int,
) {
    val context = LocalContext.current
    val is24Hour = remember(context) { context.is24HourFormat }
    var showDueDatePicker by rememberSaveable { mutableStateOf(false) }
    var showDiscardConfirmation by rememberSaveable { mutableStateOf(false) }
    var showDeleteConfirmation by rememberSaveable { mutableStateOf(false) }
    DisposableEffect(Unit) {
        val activity = context.findActivity()
        if (atLeastOreoMR1() && viewState.showEditScreenWithoutUnlock) {
            activity?.setShowWhenLocked(true)
        }

        onDispose {
            if (atLeastOreoMR1()) {
                activity?.setShowWhenLocked(false)
            }
        }
    }
    val requestDiscard = {
        if (editViewModel.hasChanges()) {
            val prompt = { showDiscardConfirmation = true }
            context.findActivity()?.hideKeyboardThen(prompt) ?: prompt()
        } else {
            discard()
        }
    }
    val requestDelete = {
        val prompt = { showDeleteConfirmation = true }
        context.findActivity()?.hideKeyboardThen(prompt) ?: prompt()
    }
    val onBackPressed = {
        if (viewState.backButtonSavesTask) {
            save()
        } else {
            requestDiscard()
        }
    }
    BackHandler {
        Timber.d("onBackPressed")
        onBackPressed()
    }
    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors().copy(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                navigationIcon = {
                    if (viewState.isReadOnly) {
                        IconButton(onClick = { onBackPressed() }) {
                            SymbolIcon(
                                name = TasksIcons.ARROW_BACK,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    } else {
                        // DavTasks: patrón de diálogo a pantalla completa de Material 3:
                        // X para descartar y botón "Guardar" a la derecha.
                        IconButton(onClick = { requestDiscard() }) {
                            SymbolIcon(
                                name = TasksIcons.CLEAR,
                                contentDescription = org.jetbrains.compose.resources.stringResource(
                                    Res.string.menu_discard_changes
                                ),
                            )
                        }
                    }
                },
                title = {
                    if (viewState.isNew && !viewState.isReadOnly) {
                        Text(
                            text = org.jetbrains.compose.resources.stringResource(Res.string.new_task),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                },
                actions = {
                    if (viewState.isReadOnly) {
                        return@TopAppBar
                    }
                    if (!viewState.isNew) {
                        IconButton(onClick = { requestDelete() }) {
                            SymbolIcon(
                                name = TasksIcons.DELETE,
                                contentDescription = org.jetbrains.compose.resources.stringResource(
                                    Res.string.delete_task
                                ),
                            )
                        }
                    }
                    FilledTonalButton(
                        onClick = { save() },
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag(TestTags.SAVE_TASK),
                    ) {
                        Text(text = stringResource(R.string.save))
                    }
                },
            )
        },
        bottomBar = {
            if (viewState.showComments && !viewState.isReadOnly) {
                AndroidFragment<CommentBarFragment>(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(WindowInsets.navigationBars.asPaddingValues())
                )
            }
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .gesturesDisabled(viewState.isReadOnly)
                .padding(paddingValues)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            val scope = rememberCoroutineScope()
            val hasSubtasks by editViewModel.hasSubtasks.collectAsStateWithLifecycle()
            var showAllFields by rememberSaveable { mutableStateOf(false) }
            val withValue = remember(viewState, hasSubtasks) {
                viewState.displayOrder
                    .filter { fieldHasValue(it, viewState, hasSubtasks, preferences) }
                    .toSet()
            }
            // Un campo que ha tenido valor no desaparece aunque se vacíe mientras se edita.
            var revealed by remember { mutableStateOf(emptySet<Int>()) }
            LaunchedEffect(withValue) { revealed = revealed + withValue }
            val visibleFields = if (showAllFields || viewState.isReadOnly) {
                viewState.displayOrder
            } else {
                viewState.displayOrder.filter {
                    it in ALWAYS_VISIBLE_FIELDS || it in withValue || it in revealed
                }
            }
            visibleFields.forEach { tag ->
                when (tag) {
                    TAG_TITLE -> {
                        TitleRow(
                            text = viewState.task.title,
                            onChanged = { text: CharSequence? ->
                                editViewModel.setTitle(text.toString().trim { it <= ' ' })
                            },
                            linkify = linkify,
                            markdownProvider = markdownProvider,
                            isCompleted = viewState.isCompleted,
                            isRecurring = viewState.task.isRecurring,
                            onComplete = {
                                if (viewState.isCompleted) {
                                    editViewModel.setComplete(false)
                                } else {
                                    editViewModel.setComplete(true)
                                    scope.launch {
                                        save()
                                    }
                                }
                            },
                            requestFocus = viewState.showKeyboard,
                            multiline = viewState.multilineTitle,
                            save = {
                                scope.launch {
                                    save()
                                }
                            },
                        )
                    }

                    TAG_DUE_DATE -> DueDateRow(
                        dueDate = editViewModel.dueDate.collectAsStateWithLifecycle().value,
                        hasDueDateAlarm = remember (viewState.alarms) {
                            viewState.alarms.any { it.type == Alarm.TYPE_REL_END }
                        },
                        is24HourFormat = is24Hour,
                        alwaysDisplayFullDate = viewState.alwaysDisplayFullDate,
                        onClick = {
                            val showPicker = { showDueDatePicker = true }
                            context.findActivity()?.hideKeyboardThen(showPicker) ?: showPicker()
                        },
                    )
                    TAG_PRIORITY ->
                        PriorityRow(
                            priority = viewState.task.priority,
                            onChangePriority = { editViewModel.setPriority(it) },
                        )

                    TAG_DESCRIPTION ->
                        DescriptionRow(
                            text = viewState.task.notes,
                            onChanged = { text -> editViewModel.setDescription(text.toString().trim { it <= ' ' }) },
                            linkify = if (viewState.linkify) linkify else null,
                            markdownProvider = markdownProvider,
                        )

                    TAG_LIST -> {
                        val listPickerLauncher = rememberLauncherForActivityResult(
                            contract = ActivityResultContracts.StartActivityForResult()
                        ) { it ->
                            it.data
                                ?.let { getParcelableExtra(it, EXTRA_FILTER, CaldavFilter::class.java) }
                                ?.let { editViewModel.setList(it) }
                        }
                        ListRow(
                            list = viewState.list,
                            colorProvider = colorProvider,
                            onClick = {
                                listPickerLauncher.launch(
                                    context = context,
                                    selectedFilter = viewState.list,
                                    listsOnly = true
                                )
                            }
                        )
                    }

                    TAG_CREATION ->
                        InfoRow(
                            creationDate = viewState.task.creationDate,
                            modificationDate = viewState.task.modificationDate,
                            completionDate = viewState.task.completionDate,
                            locale = locale,
                        )

                    CalendarControlSet.TAG -> AndroidFragment<CalendarControlSet>()
                    StartDateControlSet.TAG -> AndroidFragment<StartDateControlSet>()
                    ReminderControlSet.TAG -> AndroidFragment<ReminderControlSet>()
                    LocationControlSet.TAG -> AndroidFragment<LocationControlSet>()
                    FilesControlSet.TAG -> AndroidFragment<FilesControlSet>()
                    TimerControlSet.TAG -> AndroidFragment<TimerControlSet>()
                    TagsControlSet.TAG -> AndroidFragment<TagsControlSet>()
                    RepeatControlSet.TAG -> {
                        val dueDate = editViewModel.dueDate.collectAsStateWithLifecycle().value
                        LaunchedEffect(dueDate) {
                            editViewModel.onDueDateChanged()
                        }
                        RepeatRow(
                            recurrence = repeatRuleToString(viewState.task.recurrence),
                            repeatFrom = viewState.task.repeatFrom,
                            onClick = onClickRepeat,
                            onRepeatFromChanged = { editViewModel.setRepeatFrom(it) },
                        )
                    }
                    SubtaskControlSet.TAG -> AndroidFragment<SubtaskControlSet>()
                    else -> throw IllegalArgumentException("Unknown row: $tag")
                }
            }
            if (visibleFields.size < viewState.displayOrder.size) {
                TextButton(
                    onClick = { showAllFields = true },
                    modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 8.dp),
                ) {
                    SymbolIcon(
                        name = TasksIcons.EXPAND_MORE,
                        contentDescription = null,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = org.jetbrains.compose.resources.stringResource(Res.string.more_options),
                    )
                }
            }
            if (viewState.showComments) {
                CommentsRow(
                    comments = comments,
                    copyCommentToClipboard = { copyToClipboard(context, R.string.comment, it) },
                    deleteComment = deleteComment,
                    openImage = { FileHelper.startActionView(context, it) },
                )
            }
        }
        if (showDueDatePicker) {
            val dueDate = editViewModel.dueDate.collectAsStateWithLifecycle().value
            val (initialDay, initialTime) = dueDateToSelection(dueDate)
            DueDatePickerSheet(
                initialDay = initialDay,
                initialTime = initialTime,
                is24Hour = is24Hour,
                autoClose = preferences.getBoolean(R.string.p_auto_dismiss_datetime_edit_screen, false),
                showNoDate = !viewState.task.isRecurring,
                times = remember { preferences.quickPickTimes },
                initialDateInputMode = preferences.calendarDisplayMode == DisplayMode.Input,
                onDateInputModeChange = {
                    preferences.calendarDisplayMode = if (it) DisplayMode.Input else DisplayMode.Picker
                },
                initialTimeInputMode = preferences.timeDisplayMode == DisplayMode.Input,
                onTimeInputModeChange = {
                    preferences.timeDisplayMode = if (it) DisplayMode.Input else DisplayMode.Picker
                },
                onSelected = { day, time ->
                    editViewModel.setDueDate(dueDateFromSelection(day, time))
                    showDueDatePicker = false
                },
                onDismiss = { showDueDatePicker = false },
            )
        }
        if (showDiscardConfirmation) {
            DiscardChangesDialog(
                onDiscard = {
                    showDiscardConfirmation = false
                    discard()
                },
                onDismiss = { showDiscardConfirmation = false },
            )
        }
        if (showDeleteConfirmation) {
            DeleteTaskDialog(
                onDelete = {
                    showDeleteConfirmation = false
                    delete()
                },
                onDismiss = { showDeleteConfirmation = false },
            )
        }
    }
}

/** Campos que se muestran siempre; el resto se pliega en "Más opciones" si está vacío. */
private val ALWAYS_VISIBLE_FIELDS = setOf(
    TAG_TITLE,
    TAG_DUE_DATE,
    TAG_LIST,
    TAG_PRIORITY,
    TAG_DESCRIPTION,
    TagsControlSet.TAG,
)

private fun fieldHasValue(
    tag: Int,
    viewState: TaskEditViewState,
    hasSubtasks: Boolean,
    preferences: Preferences,
): Boolean {
    val task = viewState.task
    return when (tag) {
        StartDateControlSet.TAG ->
            task.hideUntil > 0 ||
                    (viewState.isNew && preferences.getIntegerFromString(
                        R.string.p_default_hideUntil_key,
                        Task.HIDE_UNTIL_NONE,
                    ) != Task.HIDE_UNTIL_NONE)
        RepeatControlSet.TAG -> !task.recurrence.isNullOrBlank()
        ReminderControlSet.TAG -> viewState.alarms.isNotEmpty()
        LocationControlSet.TAG -> viewState.location != null
        FilesControlSet.TAG ->
            viewState.attachments.isNotEmpty() || task.hasTransitory(TaskAttachment.KEY)
        TimerControlSet.TAG ->
            task.estimatedSeconds > 0 || task.elapsedSeconds > 0 || task.timerStart > 0
        CalendarControlSet.TAG -> !task.calendarURI.isNullOrBlank() || viewState.calendar != null
        SubtaskControlSet.TAG -> hasSubtasks || viewState.newSubtasks.isNotEmpty()
        else -> false
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun TaskEditScreenPreview() {
    TasksTheme {
//        TaskEditScreen(
//
//        )
    }
}
