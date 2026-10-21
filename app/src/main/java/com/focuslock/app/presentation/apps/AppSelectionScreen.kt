package com.focuslock.app.presentation.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.focuslock.app.core.designsystem.AmoledBackground
import com.focuslock.app.core.designsystem.CyanAccent
import com.focuslock.app.core.designsystem.GlassmorphicCard
import com.focuslock.app.core.designsystem.PurpleAccent
import com.focuslock.app.core.designsystem.TextMuted
import com.focuslock.app.core.designsystem.TextPrimary
import com.focuslock.app.core.designsystem.TextSecondary
import com.focuslock.app.data.repository.InstalledAppInfo
import com.focuslock.app.domain.model.AppCategory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSelectionScreen(
    onBack: () -> Unit,
    viewModel: AppSelectionViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AmoledBackground)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // App Bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "App Discovery & Selection",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "Choose which apps to control with daily limits",
                    fontSize = 12.sp,
                    color = TextMuted
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Search Field
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = { viewModel.onSearchQueryChanged(it) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search installed apps...", color = TextMuted) },
            leadingIcon = {
                Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = TextMuted)
            },
            shape = RoundedCornerShape(16.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF131520),
                unfocusedContainerColor = Color(0xFF131520),
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedIndicatorColor = CyanAccent,
                unfocusedIndicatorColor = Color(0xFF262C40)
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Category Filter Chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                FilterChip(
                    selected = uiState.selectedCategory == null,
                    onClick = { viewModel.onCategorySelected(null) },
                    label = { Text("All Apps") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CyanAccent,
                        selectedLabelColor = AmoledBackground,
                        containerColor = Color(0xFF161925),
                        labelColor = TextSecondary
                    )
                )
            }
            items(AppCategory.entries) { category ->
                FilterChip(
                    selected = uiState.selectedCategory == category,
                    onClick = { viewModel.onCategorySelected(category) },
                    label = { Text(category.title) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CyanAccent,
                        selectedLabelColor = AmoledBackground,
                        containerColor = Color(0xFF161925),
                        labelColor = TextSecondary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // App List
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = CyanAccent)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(uiState.filteredApps, key = { it.packageName }) { appInfo ->
                    AppSelectionRow(
                        app = appInfo,
                        onToggle = { viewModel.toggleAppProtection(appInfo) },
                        onLimitSelected = { newLimit -> viewModel.updateAppLimit(appInfo, newLimit) }
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(30.dp))
                }
            }
        }
    }
}

@Composable
fun AppSelectionRow(
    app: InstalledAppInfo,
    onToggle: () -> Unit,
    onLimitSelected: (Long) -> Unit
) {
    val limits = listOf(15 * 60L, 30 * 60L, 45 * 60L, 60 * 60L)

    GlassmorphicCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        backgroundColor = if (app.isProtected) Color(0xFF141829) else Color(0xFF10121B)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (app.isProtected) CyanAccent.copy(alpha = 0.2f) else Color(0xFF1E2232)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = app.appName.take(1).uppercase(),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (app.isProtected) CyanAccent else TextSecondary
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = app.appName,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = app.category.title,
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                    }
                }

                Switch(
                    checked = app.isProtected,
                    onCheckedChange = { onToggle() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = AmoledBackground,
                        checkedTrackColor = CyanAccent,
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = Color(0xFF1F2436)
                    )
                )
            }

            if (app.isProtected) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Daily limit:",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        limits.forEach { limitSec ->
                            val mins = limitSec / 60
                            val isSelected = app.dailyLimitSeconds == limitSec
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) PurpleAccent else Color(0xFF1E2234))
                                    .clickable { onLimitSelected(limitSec) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "${mins}m",
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.White else TextMuted
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
