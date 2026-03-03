package com.echo.app.ui.screens

import android.app.TimePickerDialog
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echo.app.model.CalendarEvent
import com.echo.app.ui.theme.*
import com.echo.app.viewmodel.EchoViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(viewModel: EchoViewModel) {
    val events by viewModel.calendarEvents.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.loadCalendarEvents() }

    val pending = events.count { it.is_done == 0 }
    val done = events.count { it.is_done == 1 }
    val today = SimpleDateFormat("EEEE", Locale.getDefault()).format(Date())
    val todayDate = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(Date())

    Box(modifier = Modifier.fillMaxSize().background(BgPrimary)) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ─── Premium Header ───────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF0D2137),
                                Color(0xFF0A1A2E),
                                BgPrimary
                            )
                        )
                    )
                    .padding(horizontal = 20.dp, vertical = 20.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                today.uppercase(),
                                color = CyanPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                todayDate,
                                color = TextPrimary,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        // Circular progress indicator showing completion
                        Box(contentAlignment = Alignment.Center) {
                            val total = (pending + done).coerceAtLeast(1)
                            val progress = done.toFloat() / total
                            CircularProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.size(50.dp),
                                color = GreenAccent,
                                trackColor = BgCardBorder,
                                strokeWidth = 4.dp
                            )
                            Text(
                                "$done/$total",
                                color = TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Stats row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CalStatCard(
                            icon = Icons.Default.Schedule,
                            label = "Pending",
                            value = "$pending",
                            color = CyanPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        CalStatCard(
                            icon = Icons.Default.CheckCircle,
                            label = "Done",
                            value = "$done",
                            color = GreenAccent,
                            modifier = Modifier.weight(1f)
                        )
                        CalStatCard(
                            icon = Icons.Default.CalendarToday,
                            label = "Total",
                            value = "${pending + done}",
                            color = PurpleAccent,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // ─── Connection Warning ──────────────────────────────
            if (!isConnected) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = YellowAccent.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, YellowAccent.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.WifiOff, contentDescription = null, tint = YellowAccent, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Connect to PC to sync calendar", color = YellowAccent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            // ─── Event List ──────────────────────────────────────
            if (events.isEmpty() && isConnected) {
                // Empty state
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(CyanPrimary.copy(alpha = 0.08f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.EventAvailable,
                                contentDescription = null,
                                tint = CyanPrimary.copy(alpha = 0.5f),
                                modifier = Modifier.size(40.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        Text("No scheduled tasks", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Schedule a command and Echo\nwill execute it on your PC at that time",
                            color = TextMuted,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = { showAddDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Schedule Task", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else if (events.isNotEmpty()) {
                // Section label
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("SCHEDULED TASKS", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    Text("${events.size} tasks", color = TextMuted, fontSize = 11.sp)
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    // Pending tasks first
                    val pendingEvents = events.filter { it.is_done == 0 }
                    val doneEvents = events.filter { it.is_done == 1 }

                    if (pendingEvents.isNotEmpty()) {
                        items(pendingEvents, key = { it.id }) { event ->
                            PremiumEventCard(
                                event = event,
                                onComplete = { viewModel.completeCalendarEvent(event.id) },
                                onDelete = { viewModel.deleteCalendarEvent(event.id) }
                            )
                        }
                    }

                    if (doneEvents.isNotEmpty()) {
                        item {
                            Text(
                                "COMPLETED",
                                color = GreenAccent.copy(alpha = 0.6f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.5.sp,
                                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                            )
                        }
                        items(doneEvents, key = { it.id }) { event ->
                            PremiumEventCard(
                                event = event,
                                onComplete = { },
                                onDelete = { viewModel.deleteCalendarEvent(event.id) }
                            )
                        }
                    }
                }
            }
        }

        // ─── Floating Action Button ──────────────────────────
        if (events.isNotEmpty() || !isConnected) {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
                containerColor = CyanPrimary,
                contentColor = Color.Black,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add task", modifier = Modifier.size(26.dp))
            }
        }
    }

    // ─── Add Event Dialog ────────────────────────────────────
    if (showAddDialog) {
        AddEventDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { title, date, time, desc ->
                val remindAt = if (time.isNotBlank()) "$date $time" else null
                viewModel.addCalendarEvent(title, date, time, desc, remindAt)
                showAddDialog = false
            }
        )
    }
}


// ─── Stat Card ──────────────────────────────────────────────
@Composable
fun CalStatCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.15f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(value, color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(label, color = color.copy(alpha = 0.7f), fontSize = 9.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}


// ─── Premium Event Card ─────────────────────────────────────
@Composable
fun PremiumEventCard(event: CalendarEvent, onComplete: () -> Unit, onDelete: () -> Unit) {
    val isDone = event.is_done == 1
    val accentColor = if (isDone) GreenAccent else CyanPrimary

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isDone) BgCard.copy(alpha = 0.5f) else BgCard,
        border = BorderStroke(1.dp, if (isDone) GreenAccent.copy(alpha = 0.15f) else BgCardBorder),
        modifier = Modifier.drawBehind {
            // Colored left accent bar
            drawRoundRect(
                color = accentColor,
                topLeft = Offset(0f, 8.dp.toPx()),
                size = Size(4.dp.toPx(), size.height - 16.dp.toPx()),
                cornerRadius = CornerRadius(2.dp.toPx())
            )
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkbox
            IconButton(
                onClick = { if (!isDone) onComplete() },
                modifier = Modifier.size(34.dp)
            ) {
                if (isDone) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(GreenAccent.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Done", tint = GreenAccent, modifier = Modifier.size(16.dp))
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .border(2.dp, CyanPrimary.copy(alpha = 0.35f), CircleShape)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Title
                Text(
                    text = event.title,
                    color = if (isDone) TextMuted else TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Date + time row
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Date chip
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = PurpleAccent.copy(alpha = 0.1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CalendarToday, contentDescription = null, tint = PurpleAccent, modifier = Modifier.size(10.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(event.event_date, color = PurpleAccent, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                        }
                    }

                    if (event.event_time.isNotBlank()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        // Time chip
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = YellowAccent.copy(alpha = 0.1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Schedule, contentDescription = null, tint = YellowAccent, modifier = Modifier.size(10.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(event.event_time, color = YellowAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (isDone) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = GreenAccent.copy(alpha = 0.1f)
                        ) {
                            Text(
                                "EXECUTED",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                color = GreenAccent,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }

                // Description
                if (event.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        event.description,
                        color = TextMuted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Delete button
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Delete", tint = TextMuted.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
            }
        }
    }
}


// ─── Add Event Dialog ───────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEventDialog(onDismiss: () -> Unit, onAdd: (String, String, String, String) -> Unit) {
    val context = LocalContext.current
    val cal = Calendar.getInstance()

    var title by remember { mutableStateOf("") }
    var date by remember {
        mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()))
    }
    var time by remember {
        mutableStateOf(String.format("%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE)))
    }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgCard,
        title = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(CyanPrimary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.RocketLaunch, contentDescription = null, tint = CyanPrimary, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Schedule Task", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text("Auto-executes on your PC", color = CyanPrimary, fontSize = 11.sp)
                    }
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Title / command
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("e.g. Open YouTube in Chrome", color = TextMuted) },
                    label = { Text("Command", color = TextMuted, fontSize = 11.sp) },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Terminal, contentDescription = null, tint = CyanPrimary, modifier = Modifier.size(18.dp)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanPrimary,
                        unfocusedBorderColor = BgCardBorder,
                        cursorColor = CyanPrimary,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = BgSecondary,
                        unfocusedContainerColor = BgSecondary
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                // Date + Time row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Date
                    OutlinedTextField(
                        value = date,
                        onValueChange = { date = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("Date", color = TextMuted, fontSize = 11.sp) },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null, tint = PurpleAccent, modifier = Modifier.size(16.dp)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PurpleAccent,
                            unfocusedBorderColor = BgCardBorder,
                            cursorColor = PurpleAccent,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedContainerColor = BgSecondary,
                            unfocusedContainerColor = BgSecondary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Time — clickable, opens TimePickerDialog
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = time,
                            onValueChange = {},
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Time", color = TextMuted, fontSize = 11.sp) },
                            singleLine = true,
                            readOnly = true,
                            enabled = false,
                            leadingIcon = { Icon(Icons.Default.Schedule, contentDescription = null, tint = YellowAccent, modifier = Modifier.size(16.dp)) },
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledBorderColor = BgCardBorder,
                                disabledTextColor = TextPrimary,
                                disabledContainerColor = BgSecondary,
                                disabledLabelColor = TextMuted,
                                disabledLeadingIconColor = YellowAccent
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                        // Transparent overlay for click
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable {
                                    val parts = time.split(":")
                                    val h = parts.getOrNull(0)?.toIntOrNull() ?: cal.get(Calendar.HOUR_OF_DAY)
                                    val m = parts.getOrNull(1)?.toIntOrNull() ?: cal.get(Calendar.MINUTE)
                                    TimePickerDialog(context, { _, hour, minute ->
                                        time = String.format("%02d:%02d", hour, minute)
                                    }, h, m, true).show()
                                }
                        )
                    }
                }

                // Description
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Extra instructions (optional)", color = TextMuted) },
                    maxLines = 2,
                    leadingIcon = { Icon(Icons.Default.Notes, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanPrimary,
                        unfocusedBorderColor = BgCardBorder,
                        cursorColor = CyanPrimary,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = BgSecondary,
                        unfocusedContainerColor = BgSecondary
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank() && date.isNotBlank()) {
                        onAdd(title.trim(), date.trim(), time.trim(), description.trim())
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                shape = RoundedCornerShape(10.dp),
                enabled = title.isNotBlank()
            ) {
                Icon(Icons.Default.RocketLaunch, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Black)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Schedule", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextMuted)
            }
        }
    )
}
