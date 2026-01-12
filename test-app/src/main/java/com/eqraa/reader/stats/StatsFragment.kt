/*
 * Copyright 2024 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package com.eqraa.reader.stats

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.eqraa.reader.Application

class StatsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                MaterialTheme {
                    StatsScreen(application = requireActivity().application as Application)
                }
            }
        }
    }
}

// Color constants
private val PrimaryBlue = Color(0xFF137FEC)
private val BackgroundLight = Color(0xFFF6F7F8)
private val TextPrimary = Color(0xFF0F172A)
private val TextSecondary = Color(0xFF64748B)
private val GradientStart = Color(0xFF1E3A5F)
private val GradientEnd = Color(0xFF0F172A)

@Composable
fun StatsScreen(application: Application) {
    val scope = rememberCoroutineScope()
    var totalBooks by remember { mutableStateOf(0) }
    
    // Real data from StatsRepository
    var totalReadingTimeMs by remember { mutableStateOf(0L) }
    var weeklyReadingTimeMs by remember { mutableStateOf(0L) }
    var lastWeekReadingTimeMs by remember { mutableStateOf(0L) }
    var currentStreak by remember { mutableStateOf(0) }
    var activityData by remember { mutableStateOf(listOf(0L, 0L, 0L, 0L, 0L, 0L, 0L)) }
    var activeDays by remember { mutableStateOf(emptySet<Int>()) }
    
    // Placeholder data for stats not yet tracked
    val pagesRead = "—"
    val readingSpeed = "—"
    
    // Convert total reading time to hours and minutes
    val readingTimeHours = (totalReadingTimeMs / (1000 * 60 * 60)).toInt()
    val readingTimeMinutes = ((totalReadingTimeMs / (1000 * 60)) % 60).toInt()
    
    // Calculate week-over-week comparison
    val weekComparison = if (lastWeekReadingTimeMs > 0) {
        val diff = ((weeklyReadingTimeMs - lastWeekReadingTimeMs) * 100 / lastWeekReadingTimeMs).toInt()
        if (diff >= 0) "+$diff%" else "$diff%"
    } else if (weeklyReadingTimeMs > 0) {
        "+100%"
    } else {
        "—"
    }
    
    // Current month/year for calendar
    val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
    val currentMonth = java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) + 1
    val today = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_MONTH)
    
    LaunchedEffect(Unit) {
        scope.launch {
            try {
                // Fetch books count
                val books = application.bookRepository.books().first()
                totalBooks = books.size
                
                // Fetch reading stats
                totalReadingTimeMs = application.statsRepository.totalReadingTimeMs().first()
                weeklyReadingTimeMs = application.statsRepository.readingTimeThisWeekMs().first()
                lastWeekReadingTimeMs = application.statsRepository.readingTimeLastWeekMs().first()
                currentStreak = application.statsRepository.currentStreak()
                activityData = application.statsRepository.activityForLast7Days().first()
                activeDays = application.statsRepository.activeDaysForMonth(currentYear, currentMonth).first().toSet()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "STATISTICS",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                letterSpacing = 1.sp
            )
        }
        
        Divider(color = Color.LightGray, thickness = 1.dp)

        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            // Total Reading Time Section
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Text(
                        text = "TOTAL READING TIME",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = readingTimeHours.toString(),
                            fontSize = 96.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            lineHeight = 64.sp
                        )
                        Text(
                            text = "h",
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = readingTimeMinutes.toString(),
                            fontSize = 96.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            lineHeight = 64.sp
                        )
                        Text(
                            text = "m",
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Comparison Badge
                    Box(
                        modifier = Modifier
                            .border(1.dp, Color.Black, RoundedCornerShape(0.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "↗",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "$weekComparison vs last week",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Divider(color = Color.Black, thickness = 1.dp)
            }

            // Stats Grid (2x2)
            item {
                Column {
                    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                        // Books
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(24.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "BOOKS",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Gray
                                    )
                                    Icon(
                                        Icons.Default.MenuBook,
                                        contentDescription = null,
                                        modifier = Modifier.size(28.dp),
                                        tint = Color.Black
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = totalBooks.toString(),
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black
                                )
                            }
                        }
                        
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .fillMaxHeight()
                                .background(Color.Black)
                        )
                        
                        // Pages
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(24.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "PAGES",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Gray
                                    )
                                    Icon(
                                        Icons.Default.ImportContacts,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = Color.Gray
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = pagesRead,
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black
                                )
                            }
                        }
                    }
                    Divider(color = Color.Black, thickness = 1.dp)
                    
                    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                        // Speed
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(24.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "SPEED",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Gray
                                    )
                                    Icon(
                                        Icons.Default.Speed,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = Color.Gray
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = readingSpeed.toString(),
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black
                                )
                                Text(
                                    text = "WPM",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Gray
                                )
                            }
                        }
                        
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .fillMaxHeight()
                                .background(Color.Black)
                        )
                        
                        // Streak
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(24.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "STREAK",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Gray
                                    )
                                    Icon(
                                        Icons.Default.LocalFireDepartment,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = Color.Black
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = currentStreak.toString(),
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black
                                )
                                Text(
                                    text = "DAYS",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                    Divider(color = Color.Black, thickness = 1.dp)
                }
            }
            
            // Activity Chart
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "ACTIVITY",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                            Text(
                                text = "Last 7 Days",
                                fontSize = 16.sp,
                                color = Color.Gray
                            )
                        }
                        Text(
                            text = "${weeklyReadingTimeMs / (1000 * 60 * 60)}h",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    // Bar Chart
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        val days = listOf("M", "T", "W", "T", "F", "S", "S")
                        val maxActivity = activityData.maxOrNull()?.coerceAtLeast(1L) ?: 1L
                        val values = activityData.map { (it.toFloat() / maxActivity).coerceIn(0f, 1f) }
                        val todayDayOfWeek = (java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7 // Convert to Mon=0 index
                        
                        days.forEachIndexed { index, day ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(32.dp)
                                        .height(80.dp) // Max height container
                                        .background(Color(0xFFF5F5F5)) // Light gray background
                                        ,
                                    contentAlignment = Alignment.BottomCenter
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .fillMaxHeight(values[index])
                                            .background(
                                                if (index == todayDayOfWeek) Color.Black else Color(0xFF9CA3AF)
                                            )
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = day,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (index == todayDayOfWeek) Color.Black else Color.Gray
                                )
                            }
                        }
                    }
                }
                Divider(color = Color.Black, thickness = 1.dp)
            }
            
            // History Calendar
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "HISTORY",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.ChevronLeft,
                                contentDescription = "Previous Month",
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = java.text.SimpleDateFormat("MMM yyyy", java.util.Locale.US).format(java.util.Calendar.getInstance().time),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = "Next Month",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Calendar Grid
                    val days = listOf("S", "M", "T", "W", "T", "F", "S")
                    Row(modifier = Modifier.fillMaxWidth()) {
                        days.forEach { day ->
                            Text(
                                text = day,
                                modifier = Modifier.weight(1f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                fontSize = 10.sp,
                                color = Color.Gray,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Calendar data from current month
                    val calendar = java.util.Calendar.getInstance()
                    val daysInMonth = calendar.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
                    val calendarDays = (1..daysInMonth).toList()
                    calendar.set(java.util.Calendar.DAY_OF_MONTH, 1)
                    val startingDayOffset = (calendar.get(java.util.Calendar.DAY_OF_WEEK) - 1) // Sun=0
                    
                    // Simple grid using Column/Row
                    val rows = (calendarDays.size + startingDayOffset + 6) / 7
                    for (row in 0 until rows) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            for (col in 0 until 7) {
                                val dayIndex = row * 7 + col - startingDayOffset
                                if (dayIndex in 0 until calendarDays.size) {
                                    val dayNum = calendarDays[dayIndex]
                                    val isActive = activeDays.contains(dayNum)
                                    val isToday = dayNum == today
                                    
                                    Box(
                                        modifier = Modifier.weight(1f),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isActive) {
                                            Box(
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .background(Color.Black)
                                            )
                                        } else if (isToday) {
                                             Box(
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .border(2.dp, Color.Black)
                                            )
                                        }
                                        
                                        Text(
                                            text = dayNum.toString(),
                                            fontSize = 14.sp,
                                            color = if (isActive) Color.White else Color.Black
                                        )
                                    }
                                } else {
                                    Box(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
                
                // Bottom padding for floating nav
                Spacer(modifier = Modifier.height(100.dp))
            }
        }
    }
}
