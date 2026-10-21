package com.focuslock.app.presentation.apps

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import com.focuslock.app.core.designsystem.AnimatedPrimaryButton
import com.focuslock.app.core.designsystem.BrandCyan
import com.focuslock.app.core.designsystem.BrandIndigo
import com.focuslock.app.core.designsystem.BrandTeal
import com.focuslock.app.core.designsystem.DangerRed
import com.focuslock.app.core.designsystem.DarkBackground
import com.focuslock.app.core.designsystem.DarkBorder
import com.focuslock.app.core.designsystem.DarkBorderSubtle
import com.focuslock.app.core.designsystem.DarkSurface
import com.focuslock.app.core.designsystem.DarkSurfaceElevated
import com.focuslock.app.core.designsystem.EmptyState
import com.focuslock.app.core.designsystem.FocusCard
import com.focuslock.app.core.designsystem.FocusLockBottomBar
import com.focuslock.app.core.designsystem.FocusLockTopBar
import com.focuslock.app.core.designsystem.Neutral200
import com.focuslock.app.core.designsystem.Neutral400
import com.focuslock.app.core.designsystem.Neutral600
import com.focuslock.app.core.designsystem.WarningAmber
import com.focuslock.app.core.util.TimeUtils
import com.focuslock.app.domain.model.AppCategory

@Composable
fun AppSelectionScreen(
    onBack: () -> Unit,
    onNavigateToDetails: (String) -> Unit,
    showBottomBar: Boolean = false,
    onNavigateToHome: () -> Unit = {},
    onNavigateToFocus: () -> Unit = {},
    onNavigateToAnalytics: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    viewModel: AppSelectionViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showSortMenu by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = DarkBackground,
        bottomBar = {
            if (showBottomBar) {
                FocusLockBottomBar(
                    currentRoute = "apps",
                    onNavigateToHome = onNavigateToHome,
                    onNavigateToApps = { /* Already on apps */ },
                    onNavigateToFocus = onNavigateToFocus,
                    onNavigateToAnalytics = onNavigateToAnalytics,
                    onNavigateToSettings = onNavigateToSettings
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Screen 5 Top Bar: Back arrow, "Protected Apps", 3-dots sort menu
            FocusLockTopBar(
                title = "Protected Apps",
                navigationIcon = if (!showBottomBar) Icons.AutoMirrored.Filled.ArrowBack else null,
                onNavigationClick = onBack,
                actionIcon = Icons.AutoMirrored.Filled.Sort,
                onActionClick = { showSortMenu = true }
            )

            // Dropdown Menu for Sorting
            DropdownMenu(
                expanded = showSortMenu,
                onDismissRequest = { showSortMenu = false },
                modifier = Modifier.background(DarkSurfaceElevated)
            ) {
                AppSortOrder.entries.forEach { order ->
                    DropdownMenuItem(
                        text = { Text(order.title, color = Color.White) },
                        onClick = {
                            viewModel.onSortOrderSelected(order)
                            showSortMenu = false
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Search Bar: "Search apps..." (Screen 5)
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.onSearchQueryChanged(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                placeholder = { Text("Search apps...", color = Neutral400, fontSize = 14.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = Neutral400,
                        modifier = Modifier.size(20.dp)
                    )
                },
                shape = RoundedCornerShape(14.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = DarkSurface,
                    unfocusedContainerColor = DarkSurface,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedIndicatorColor = BrandCyan,
                    unfocusedIndicatorColor = DarkBorder
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Filter Chips: All, Social, Video, Game, Other (Screen 5)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    FilterChip(
                        selected = uiState.selectedCategory == null,
                        onClick = { viewModel.onCategorySelected(null) },
                        label = { Text("All", fontSize = 12.sp) },
                        shape = RoundedCornerShape(20.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BrandIndigo,
                            selectedLabelColor = Color.White,
                            containerColor = DarkSurface,
                            labelColor = Neutral400
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = uiState.selectedCategory == null,
                            borderColor = DarkBorder,
                            selectedBorderColor = BrandIndigo
                        )
                    )
                }

                items(AppCategory.entries) { category ->
                    FilterChip(
                        selected = uiState.selectedCategory == category,
                        onClick = { viewModel.onCategorySelected(category) },
                        label = { Text(category.title, fontSize = 12.sp) },
                        shape = RoundedCornerShape(20.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BrandIndigo,
                            selectedLabelColor = Color.White,
                            containerColor = DarkSurface,
                            labelColor = Neutral400
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = uiState.selectedCategory == category,
                            borderColor = DarkBorder,
                            selectedBorderColor = BrandIndigo
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
                    CircularProgressIndicator(color = BrandCyan, strokeWidth = 3.dp)
                }
            } else if (uiState.filteredApps.isEmpty()) {
                EmptyState(
                    message = "No matching applications found",
                    subMessage = "Try searching by a different name or clearing filters."
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    items(uiState.filteredApps, key = { it.item.packageName }) { appModel ->
                        AppListItemRow(
                            model = appModel,
                            onToggle = { viewModel.toggleAppProtection(appModel.item) },
                            onClick = { onNavigateToDetails(appModel.item.packageName) }
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                // Bottom Button: "+ Protect Suggested" or "+ Add Apps"
                if (uiState.suggestedCount > 0) {
                    AnimatedPrimaryButton(
                        text = "+ Protect ${uiState.suggestedCount} Suggested Distractions",
                        onClick = { viewModel.selectAllSuggestedDistractions() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    )
                }
            }
        }
    }
}

/**
 * Screen 5 App Row Card
 */
@Composable
private fun AppListItemRow(
    model: AppItemUiModel,
    onToggle: () -> Unit,
    onClick: () -> Unit
) {
    FocusCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        cornerRadius = 16.dp,
        backgroundColor = DarkSurface,
        borderColor = if (model.isBlocked) DangerRed.copy(alpha = 0.4f) else DarkBorder,
        contentPadding = 14.dp
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
                    if (model.item.icon != null) {
                        val bitmap = model.item.icon.toBitmap(64, 64, Bitmap.Config.ARGB_8888)
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = model.item.appName,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                    } else {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(DarkSurfaceElevated)
                        ) {
                            Text(
                                text = model.item.appName.take(1).uppercase(),
                                fontWeight = FontWeight.Bold,
                                color = BrandCyan,
                                fontSize = 15.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = model.item.appName,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            text = model.item.category.title,
                            fontSize = 12.sp,
                            color = Neutral400
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${TimeUtils.formatDurationShort(model.usedSecondsToday)} / ${TimeUtils.formatDurationShort(model.item.dailyLimitSeconds)}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (model.isBlocked) DangerRed else Neutral200
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Switch(
                        checked = model.item.isProtected,
                        onCheckedChange = { onToggle() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = BrandCyan
                        )
                    )
                }
            }

            if (model.item.isProtected) {
                Spacer(modifier = Modifier.height(10.dp))

                LinearProgressIndicator(
                    progress = { model.progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (model.isBlocked) DangerRed else if (model.progressFraction > 0.8f) WarningAmber else BrandCyan,
                    trackColor = DarkBorderSubtle,
                    strokeCap = StrokeCap.Round
                )
            }
        }
    }
}
