@file:Suppress("TooManyFunctions")

package tech.shaffersoftworks.remotescreens.player.ui.screens.player.remote

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.shaffersoftworks.remotescreens.player.ui.theme.RemoteScreensTheme
import tech.shaffersoftworks.remotescreens.shared.CalendarChoice
import tech.shaffersoftworks.remotescreens.shared.CalendarDeleteTarget
import tech.shaffersoftworks.remotescreens.shared.CalendarDialogMode
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DialogBackground = Color(0xFF333333)
private val DialogSecondary = Color(0xFFCACACA)
private val DialogTeal = Color(0xFF03DAC5)
private val DialogWhite = Color(0xFFFFFFFF)
private val CreateDialogWidth = 640.dp
private val FieldLabelSize = 14.sp
private val TitleSize = 24.sp
private val FieldGap = 12.dp
private val ErrorGap = 4.dp
private val PickInset = 8.dp
private val DisplayDate = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.US)
private val DisplayTime = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

/** What the create dialog sends once the form is valid. */
data class CalendarCreateForm(
    val entityId: String,
    val summary: String,
    val location: String,
    val isAllDay: Boolean,
    val startDate: LocalDate,
    val startTime: LocalTime,
    val endDate: LocalDate,
    val endTime: LocalTime,
)

private enum class PickerTarget {
    START_DATE,
    END_DATE,
    START_TIME,
    END_TIME,
}

/** The RPI create and delete dialogs, drawn natively because a remote document cannot host a text field. */
@Composable
fun CalendarDialogs(
    state: CalendarDialogState,
    onDismiss: () -> Unit,
    onCreate: (CalendarCreateForm) -> Unit,
    onDelete: () -> Unit,
) {
    if (state.mode == CalendarDialogMode.DELETE) {
        val event = state.event ?: return
        DeleteCalendarEventDialog(
            summary = event.summary,
            isMutating = state.isMutating,
            onDismiss = onDismiss,
            onConfirm = onDelete,
        )
    } else {
        CreateCalendarEventDialog(
            calendars = state.calendars,
            isMutating = state.isMutating,
            serverError = state.error,
            onDismiss = onDismiss,
            onCreate = onCreate,
        )
    }
}

@Composable
private fun CreateCalendarEventDialog(
    calendars: List<CalendarChoice>,
    isMutating: Boolean,
    serverError: String?,
    onDismiss: () -> Unit,
    onCreate: (CalendarCreateForm) -> Unit,
) {
    var selected by remember(calendars) { mutableStateOf(calendars.firstOrNull()) }
    var summary by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var isAllDay by remember { mutableStateOf(false) }
    val defaultStart = remember { LocalTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0) }
    var startDate by remember { mutableStateOf(LocalDate.now()) }
    var endDate by remember { mutableStateOf(LocalDate.now()) }
    var startTime by remember { mutableStateOf(defaultStart) }
    var endTime by remember { mutableStateOf(defaultStart.plusHours(1)) }
    var formError by remember { mutableStateOf<String?>(null) }
    var activePicker by remember { mutableStateOf<PickerTarget?>(null) }
    LaunchedEffect(serverError) {
        if (serverError != null) formError = serverError
    }
    val colors = calendarFieldColors()
    AlertDialog(
        onDismissRequest = { if (!isMutating) onDismiss() },
        containerColor = DialogBackground,
        modifier = Modifier.width(CreateDialogWidth),
        title = { Text(text = "New event", color = DialogWhite, fontSize = TitleSize) },
        text = {
            CreateCalendarFields(
                calendars = calendars,
                selected = selected,
                onSelected = { selected = it },
                summary = summary,
                onSummary = { summary = it },
                location = location,
                onLocation = { location = it },
                isAllDay = isAllDay,
                onAllDay = { checked ->
                    isAllDay = checked
                    endDate = endDateAfterAllDayToggle(checked, startDate, endDate)
                },
                startDate = startDate,
                endDate = endDate,
                startTime = startTime,
                endTime = endTime,
                formError = formError,
                colors = colors,
                onPick = { activePicker = it },
            )
        },
        confirmButton = {
            TextButton(
                enabled = !isMutating && calendars.isNotEmpty(),
                onClick = {
                    formError = submitNewEvent(
                        NewEventDraft(selected, summary, location, isAllDay, startDate, startTime, endDate, endTime),
                        onCreate,
                    )
                },
            ) { Text(text = "Create", color = DialogTeal) }
        },
        dismissButton = {
            TextButton(enabled = !isMutating, onClick = onDismiss) {
                Text(text = "Cancel", color = DialogSecondary)
            }
        },
    )
    CalendarPicker(
        target = activePicker,
        startDate = startDate,
        endDate = endDate,
        startTime = startTime,
        endTime = endTime,
        isAllDay = isAllDay,
        onDismiss = { activePicker = null },
        onStartDate = { picked ->
            startDate = picked
            endDate = endDateAfterStartChange(isAllDay, picked, endDate)
            activePicker = null
        },
        onEndDate = { endDate = it; activePicker = null },
        onStartTime = { picked ->
            startTime = picked
            endTime = endTimeAfterStartChange(picked, endTime, startDate, endDate)
            activePicker = null
        },
        onEndTime = { endTime = it; activePicker = null },
    )
}

@Composable
private fun calendarFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedTextColor = DialogWhite,
    unfocusedTextColor = DialogWhite,
    disabledTextColor = DialogWhite,
    focusedBorderColor = DialogTeal,
    unfocusedBorderColor = DialogSecondary,
    disabledBorderColor = DialogSecondary,
    focusedLabelColor = DialogTeal,
    unfocusedLabelColor = DialogSecondary,
    disabledLabelColor = DialogSecondary,
    cursorColor = DialogTeal,
)

@Composable
private fun CreateCalendarFields(
    calendars: List<CalendarChoice>,
    selected: CalendarChoice?,
    onSelected: (CalendarChoice) -> Unit,
    summary: String,
    onSummary: (String) -> Unit,
    location: String,
    onLocation: (String) -> Unit,
    isAllDay: Boolean,
    onAllDay: (Boolean) -> Unit,
    startDate: LocalDate,
    endDate: LocalDate,
    startTime: LocalTime,
    endTime: LocalTime,
    formError: String?,
    colors: TextFieldColors,
    onPick: (PickerTarget) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(FieldGap),
    ) {
        if (calendars.isEmpty()) {
            Text(text = "No calendars allow creating events.", color = DialogSecondary, fontSize = FieldLabelSize)
        } else {
            CalendarDropdown(calendars, selected, onSelected, colors)
        }
        OutlinedTextField(
            value = summary,
            onValueChange = onSummary,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Title") },
            singleLine = true,
            colors = colors,
        )
        OutlinedTextField(
            value = location,
            onValueChange = onLocation,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Location") },
            singleLine = true,
            colors = colors,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = isAllDay,
                onCheckedChange = onAllDay,
                colors = CheckboxDefaults.colors(
                    checkedColor = DialogTeal,
                    uncheckedColor = DialogSecondary,
                    checkmarkColor = DialogBackground,
                ),
            )
            Text(text = "All day", color = DialogWhite)
        }
        PickerField("Start date", startDate.format(DisplayDate), colors) { onPick(PickerTarget.START_DATE) }
        if (!isAllDay) {
            PickerField("Start time", startTime.format(DisplayTime), colors) { onPick(PickerTarget.START_TIME) }
        }
        PickerField("End date", endDate.format(DisplayDate), colors) { onPick(PickerTarget.END_DATE) }
        if (!isAllDay) {
            PickerField("End time", endTime.format(DisplayTime), colors) { onPick(PickerTarget.END_TIME) }
        }
        if (formError != null) {
            Text(
                text = formError,
                color = DialogTeal,
                fontSize = FieldLabelSize,
                modifier = Modifier.padding(bottom = ErrorGap),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarDropdown(
    calendars: List<CalendarChoice>,
    selected: CalendarChoice?,
    onSelected: (CalendarChoice) -> Unit,
    colors: TextFieldColors,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            readOnly = true,
            value = selected?.name.orEmpty(),
            onValueChange = {},
            label = { Text("Calendar") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = colors,
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            calendars.forEach { calendar ->
                DropdownMenuItem(
                    text = { Text(calendar.name) },
                    onClick = {
                        onSelected(calendar)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun PickerField(label: String, value: String, colors: TextFieldColors, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier.fillMaxWidth().clickable(
            interactionSource = interaction,
            indication = null,
            onClick = onClick,
        ),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            modifier = Modifier.fillMaxWidth(),
            enabled = false,
            readOnly = true,
            label = { Text(label) },
            colors = colors,
            trailingIcon = {
                Text(text = "Pick", color = DialogTeal, modifier = Modifier.padding(end = PickInset))
            },
        )
    }
}

@Composable
private fun CalendarPicker(
    target: PickerTarget?,
    startDate: LocalDate,
    endDate: LocalDate,
    startTime: LocalTime,
    endTime: LocalTime,
    isAllDay: Boolean,
    onDismiss: () -> Unit,
    onStartDate: (LocalDate) -> Unit,
    onEndDate: (LocalDate) -> Unit,
    onStartTime: (LocalTime) -> Unit,
    onEndTime: (LocalTime) -> Unit,
) {
    when (target) {
        PickerTarget.START_DATE -> CalendarDatePickerDialog(startDate, onDismiss, onStartDate)
        PickerTarget.END_DATE -> CalendarDatePickerDialog(endDate, onDismiss, onEndDate)
        PickerTarget.START_TIME -> if (!isAllDay) CalendarTimePickerDialog(startTime, onDismiss, onStartTime)
        PickerTarget.END_TIME -> if (!isAllDay) CalendarTimePickerDialog(endTime, onDismiss, onEndTime)
        null -> Unit
    }
}

@Composable
private fun CalendarDatePickerDialog(
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val millis = datePickerState.selectedDateMillis ?: return@TextButton
                onConfirm(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) { DatePicker(state = datePickerState) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarTimePickerDialog(
    initialTime: LocalTime,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit,
) {
    val timePickerState = rememberTimePickerState(
        initialHour = initialTime.hour,
        initialMinute = initialTime.minute,
        is24Hour = false,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DialogBackground,
        title = { Text(text = "Pick time", color = DialogWhite) },
        text = { TimePicker(state = timePickerState) },
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(timePickerState.hour, timePickerState.minute)) }) {
                Text(text = "OK", color = DialogTeal)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = "Cancel", color = DialogSecondary) }
        },
    )
}

@Composable
private fun DeleteCalendarEventDialog(
    summary: String,
    isMutating: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!isMutating) onDismiss() },
        containerColor = DialogBackground,
        title = { Text(text = "Delete event?", color = DialogWhite) },
        text = { Text(text = "Delete \"$summary\" from Home Assistant?", color = DialogSecondary) },
        confirmButton = {
            TextButton(enabled = !isMutating, onClick = onConfirm) {
                Text(text = "Delete", color = DialogTeal)
            }
        },
        dismissButton = {
            TextButton(enabled = !isMutating, onClick = onDismiss) {
                Text(text = "Cancel", color = DialogSecondary)
            }
        },
    )
}

private fun endDateAfterAllDayToggle(isAllDay: Boolean, startDate: LocalDate, endDate: LocalDate): LocalDate =
    when {
        isAllDay -> startDate.plusDays(1)
        !endDate.isAfter(startDate) -> startDate
        else -> endDate
    }

private fun endDateAfterStartChange(isAllDay: Boolean, picked: LocalDate, endDate: LocalDate): LocalDate = when {
    isAllDay -> picked.plusDays(1)
    endDate.isBefore(picked) -> picked
    else -> endDate
}

private fun endTimeAfterStartChange(
    picked: LocalTime,
    endTime: LocalTime,
    startDate: LocalDate,
    endDate: LocalDate,
): LocalTime = if (!endTime.isAfter(picked) && endDate == startDate) picked.plusHours(1) else endTime

private data class NewEventDraft(
    val calendar: CalendarChoice?,
    val summary: String,
    val location: String,
    val isAllDay: Boolean,
    val startDate: LocalDate,
    val startTime: LocalTime,
    val endDate: LocalDate,
    val endTime: LocalTime,
)

private fun submitNewEvent(draft: NewEventDraft, onCreate: (CalendarCreateForm) -> Unit): String? {
    val form = draft.calendar?.let { choice ->
        CalendarCreateForm(
            entityId = choice.entityId,
            summary = draft.summary,
            location = draft.location,
            isAllDay = draft.isAllDay,
            startDate = draft.startDate,
            startTime = if (draft.isAllDay) LocalTime.MIDNIGHT else draft.startTime,
            endDate = draft.endDate,
            endTime = if (draft.isAllDay) LocalTime.MIDNIGHT else draft.endTime,
        )
    }
    val error = createFormError(form)
    if (error == null && form != null) onCreate(form)
    return error
}

private fun createFormError(form: CalendarCreateForm?): String? = when {
    form == null -> "Select a calendar"
    form.summary.trim().isEmpty() -> "Title is required"
    form.isAllDay && !form.endDate.isAfter(form.startDate) -> "End date must be after start date"
    !form.isAllDay && !form.endDate.atTime(form.endTime).isAfter(form.startDate.atTime(form.startTime)) ->
        "End must be after start"
    else -> null
}

private val PreviewCalendars = listOf(
    CalendarChoice("calendar.family", "Family"),
    CalendarChoice("calendar.mike", "Mike's Calendar"),
)
private val PreviewDay = LocalDate.of(2026, 7, 15)
private val PreviewStart = LocalTime.of(17, 30)
private val PreviewEnd = LocalTime.of(18, 30)

@Preview(widthDp = 800, heightDp = 480)
@Composable
private fun CalendarDialogsPreview() {
    RemoteScreensTheme {
        CalendarDialogs(
            state = CalendarDialogState(
                mode = CalendarDialogMode.CREATE,
                tileId = "cal",
                calendars = PreviewCalendars,
            ),
            onDismiss = {},
            onCreate = {},
            onDelete = {},
        )
    }
}

@Preview(widthDp = 800, heightDp = 480)
@Composable
private fun CalendarDialogsDeletePreview() {
    RemoteScreensTheme {
        CalendarDialogs(
            state = CalendarDialogState(
                mode = CalendarDialogMode.DELETE,
                tileId = "cal",
                event = CalendarDeleteTarget(
                    entityId = "calendar.family",
                    uid = "event-1",
                    summary = "Soccer practice",
                ),
            ),
            onDismiss = {},
            onCreate = {},
            onDelete = {},
        )
    }
}

@Preview(widthDp = 800, heightDp = 480)
@Composable
private fun CalendarDialogsErrorPreview() {
    RemoteScreensTheme {
        CalendarDialogs(
            state = CalendarDialogState(
                mode = CalendarDialogMode.CREATE,
                tileId = "cal",
                calendars = PreviewCalendars,
                error = "Failed to create event",
            ),
            onDismiss = {},
            onCreate = {},
            onDelete = {},
        )
    }
}

@Preview(widthDp = 800, heightDp = 480)
@Composable
private fun CreateCalendarEventDialogPreview() {
    RemoteScreensTheme {
        CreateCalendarEventDialog(
            calendars = PreviewCalendars,
            isMutating = false,
            serverError = null,
            onDismiss = {},
            onCreate = {},
        )
    }
}

@Preview(widthDp = 800, heightDp = 480)
@Composable
private fun CreateCalendarEventDialogEmptyPreview() {
    RemoteScreensTheme {
        CreateCalendarEventDialog(
            calendars = emptyList(),
            isMutating = false,
            serverError = null,
            onDismiss = {},
            onCreate = {},
        )
    }
}

@Preview(widthDp = 800, heightDp = 480)
@Composable
private fun CreateCalendarEventDialogErrorPreview() {
    RemoteScreensTheme {
        CreateCalendarEventDialog(
            calendars = PreviewCalendars,
            isMutating = false,
            serverError = "Failed to create event",
            onDismiss = {},
            onCreate = {},
        )
    }
}

@Preview(widthDp = 800, heightDp = 480)
@Composable
private fun CreateCalendarFieldsPreview() {
    RemoteScreensTheme {
        Box(Modifier.fillMaxWidth().background(DialogBackground).padding(FieldGap)) {
            CreateCalendarFields(
                calendars = PreviewCalendars,
                selected = PreviewCalendars.first(),
                onSelected = {},
                summary = "Soccer practice",
                onSummary = {},
                location = "Field 2",
                onLocation = {},
                isAllDay = false,
                onAllDay = {},
                startDate = PreviewDay,
                endDate = PreviewDay,
                startTime = PreviewStart,
                endTime = PreviewEnd,
                formError = null,
                colors = calendarFieldColors(),
                onPick = {},
            )
        }
    }
}

@Preview(widthDp = 800, heightDp = 480)
@Composable
private fun CreateCalendarFieldsAllDayPreview() {
    RemoteScreensTheme {
        Box(Modifier.fillMaxWidth().background(DialogBackground).padding(FieldGap)) {
            CreateCalendarFields(
                calendars = PreviewCalendars,
                selected = PreviewCalendars.first(),
                onSelected = {},
                summary = "School holiday",
                onSummary = {},
                location = "",
                onLocation = {},
                isAllDay = true,
                onAllDay = {},
                startDate = PreviewDay,
                endDate = PreviewDay.plusDays(1),
                startTime = PreviewStart,
                endTime = PreviewEnd,
                formError = null,
                colors = calendarFieldColors(),
                onPick = {},
            )
        }
    }
}

@Preview(widthDp = 800, heightDp = 480)
@Composable
private fun CreateCalendarFieldsErrorPreview() {
    RemoteScreensTheme {
        Box(Modifier.fillMaxWidth().background(DialogBackground).padding(FieldGap)) {
            CreateCalendarFields(
                calendars = emptyList(),
                selected = null,
                onSelected = {},
                summary = "",
                onSummary = {},
                location = "",
                onLocation = {},
                isAllDay = false,
                onAllDay = {},
                startDate = PreviewDay,
                endDate = PreviewDay,
                startTime = PreviewStart,
                endTime = PreviewEnd,
                formError = "Title is required",
                colors = calendarFieldColors(),
                onPick = {},
            )
        }
    }
}

@Preview(widthDp = 800, heightDp = 480)
@Composable
private fun CalendarDropdownPreview() {
    RemoteScreensTheme {
        Box(Modifier.fillMaxWidth().background(DialogBackground).padding(FieldGap)) {
            CalendarDropdown(
                calendars = PreviewCalendars,
                selected = PreviewCalendars.first(),
                onSelected = {},
                colors = calendarFieldColors(),
            )
        }
    }
}

@Preview(widthDp = 800, heightDp = 480)
@Composable
private fun PickerFieldPreview() {
    RemoteScreensTheme {
        Box(Modifier.fillMaxWidth().background(DialogBackground).padding(FieldGap)) {
            PickerField(
                label = "Start date",
                value = "Wed, Jul 15, 2026",
                colors = calendarFieldColors(),
                onClick = {},
            )
        }
    }
}

@Preview(widthDp = 800, heightDp = 480)
@Composable
private fun CalendarPickerDatePreview() {
    RemoteScreensTheme {
        CalendarPicker(
            target = PickerTarget.START_DATE,
            startDate = PreviewDay,
            endDate = PreviewDay,
            startTime = PreviewStart,
            endTime = PreviewEnd,
            isAllDay = false,
            onDismiss = {},
            onStartDate = {},
            onEndDate = {},
            onStartTime = {},
            onEndTime = {},
        )
    }
}

@Preview(widthDp = 800, heightDp = 480)
@Composable
private fun CalendarPickerTimePreview() {
    RemoteScreensTheme {
        CalendarPicker(
            target = PickerTarget.START_TIME,
            startDate = PreviewDay,
            endDate = PreviewDay,
            startTime = PreviewStart,
            endTime = PreviewEnd,
            isAllDay = false,
            onDismiss = {},
            onStartDate = {},
            onEndDate = {},
            onStartTime = {},
            onEndTime = {},
        )
    }
}

@Preview(widthDp = 800, heightDp = 480)
@Composable
private fun CalendarDatePickerDialogPreview() {
    RemoteScreensTheme {
        CalendarDatePickerDialog(initialDate = PreviewDay, onDismiss = {}, onConfirm = {})
    }
}

@Preview(widthDp = 800, heightDp = 480)
@Composable
private fun CalendarTimePickerDialogPreview() {
    RemoteScreensTheme {
        CalendarTimePickerDialog(initialTime = PreviewStart, onDismiss = {}, onConfirm = {})
    }
}

@Preview(widthDp = 800, heightDp = 480)
@Composable
private fun DeleteCalendarEventDialogPreview() {
    RemoteScreensTheme {
        DeleteCalendarEventDialog(
            summary = "Soccer practice",
            isMutating = false,
            onDismiss = {},
            onConfirm = {},
        )
    }
}

@Preview(widthDp = 800, heightDp = 480)
@Composable
private fun DeleteCalendarEventDialogMutatingPreview() {
    RemoteScreensTheme {
        DeleteCalendarEventDialog(
            summary = "Soccer practice",
            isMutating = true,
            onDismiss = {},
            onConfirm = {},
        )
    }
}
