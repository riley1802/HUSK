/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ai.edge.gallery.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ListAlt
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Brush.Companion.linearGradient
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.ai.edge.gallery.GalleryTopAppBar
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.data.AppBarAction
import com.google.ai.edge.gallery.data.AppBarActionType
import com.google.ai.edge.gallery.data.BuiltInTaskId
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.ui.common.RevealingText
import com.google.ai.edge.gallery.ui.common.TaskIcon
import com.google.ai.edge.gallery.ui.common.rememberDelayedAnimationProgress
import com.google.ai.edge.gallery.ui.common.tos.AppTosDialog
import com.google.ai.edge.gallery.ui.common.tos.TosViewModel
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
import com.google.ai.edge.gallery.ui.notes.NotesViewModel
import com.google.ai.edge.gallery.ui.theme.customColors
import java.time.LocalTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "AGHomeScreen"
private const val ANIMATION_INIT_DELAY = 0L
private const val TOP_APP_BAR_ANIMATION_DURATION = 600
private const val TITLE_FIRST_LINE_ANIMATION_DURATION = 600
private const val TITLE_SECOND_LINE_ANIMATION_DURATION = 600
private const val TITLE_SECOND_LINE_ANIMATION_START =
  ANIMATION_INIT_DELAY + (TITLE_FIRST_LINE_ANIMATION_DURATION * 0.5).toInt()
private const val TASK_LIST_ANIMATION_START = TITLE_SECOND_LINE_ANIMATION_START + 110
private const val CONTENT_COMPOSABLES_ANIMATION_DURATION = 1200
private const val CONTENT_COMPOSABLES_OFFSET_Y = 16

/** Navigation destination data */
private object HomeScreenDestination {
  @StringRes val titleRes = R.string.app_name
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
  modelManagerViewModel: ModelManagerViewModel,
  tosViewModel: TosViewModel,
  notesViewModel: NotesViewModel,
  navigateToTaskScreen: (Task) -> Unit,
  onModelsClicked: () -> Unit,
  onKnowledgeBaseClicked: () -> Unit = {},
  onNotesClicked: () -> Unit = {},
  onNotesSearchClicked: () -> Unit = {},
  onNoteClicked: (noteId: String) -> Unit = {},
  enableAnimation: Boolean,
  modifier: Modifier = Modifier,
) {
  val uiState by modelManagerViewModel.uiState.collectAsState()
  var showSettingsDialog by remember { mutableStateOf(false) }
  var showTosDialog by remember { mutableStateOf(!tosViewModel.getIsTosAccepted()) }
  val scope = rememberCoroutineScope()
  val context = LocalContext.current

  val tasks = uiState.tasks

  // Show home screen content when TOS has been accepted.
  if (!showTosDialog) {
    // The code below manages the display of the model allowlist loading indicator with a
    // debounced delay so the spinner only shows when loading takes longer than 200ms.
    var loadingModelAllowlistDelayed by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.loadingModelAllowlist) {
      if (uiState.loadingModelAllowlist) {
        delay(200)
        if (uiState.loadingModelAllowlist) {
          loadingModelAllowlistDelayed = true
        }
      } else {
        loadingModelAllowlistDelayed = false
      }
    }

    if (loadingModelAllowlistDelayed) {
      Row(
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
      ) {
        CircularProgressIndicator(
          trackColor = MaterialTheme.colorScheme.surfaceVariant,
          strokeWidth = 3.dp,
          modifier = Modifier.padding(end = 8.dp).size(20.dp),
        )
        Text(
          stringResource(R.string.loading_model_list),
          style = MaterialTheme.typography.bodyMedium,
        )
      }
    }
    if (!loadingModelAllowlistDelayed && !uiState.loadingModelAllowlist) {
      val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

      val requestPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
          isGranted: Boolean ->
          if (isGranted) {
            // FCM SDK (and your app) can post notifications.
          }
        }

      LaunchedEffect(Unit) {
        delay(2000)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
              PackageManager.PERMISSION_GRANTED
          ) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
          }
        }
      }

      BackHandler(drawerState.isOpen) { scope.launch { drawerState.close() } }

      ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
          ModalDrawerSheet {
            Column(modifier = Modifier.padding(16.dp)) {
              Row(modifier = Modifier.fillMaxWidth()) {
                SquareDrawerItem(
                  label = stringResource(R.string.drawer_settings_label),
                  description = stringResource(R.string.drawer_settings_description),
                  icon = Icons.Rounded.Settings,
                  onClick = {
                    showSettingsDialog = true
                    scope.launch { drawerState.close() }
                  },
                  modifier = Modifier.weight(1f),
                  iconBrush =
                    linearGradient(
                      colors =
                        listOf(
                          MaterialTheme.customColors.taskBgGradientColors[0][0],
                          MaterialTheme.customColors.taskBgGradientColors[0][1],
                        )
                    ),
                )
                Spacer(modifier = Modifier.width(16.dp))
                SquareDrawerItem(
                  label = stringResource(R.string.drawer_models_label),
                  description = stringResource(R.string.drawer_models_description),
                  icon = Icons.AutoMirrored.Rounded.ListAlt,
                  onClick = {
                    scope.launch { drawerState.close() }
                    scope.launch {
                      delay(50)
                      onModelsClicked()
                    }
                  },
                  modifier = Modifier.weight(1f),
                  iconBrush =
                    linearGradient(
                      colors =
                        listOf(
                          MaterialTheme.customColors.taskBgGradientColors[0][0],
                          MaterialTheme.customColors.taskBgGradientColors[0][1],
                        )
                    ),
                )
              }
              Spacer(modifier = Modifier.height(16.dp))
              Row(modifier = Modifier.fillMaxWidth()) {
                SquareDrawerItem(
                  label = "Knowledge Base",
                  description = "Manage documents",
                  icon = Icons.AutoMirrored.Rounded.MenuBook,
                  onClick = {
                    scope.launch { drawerState.close() }
                    scope.launch {
                      delay(50)
                      onKnowledgeBaseClicked()
                    }
                  },
                  modifier = Modifier.weight(1f),
                  iconBrush =
                    linearGradient(
                      colors =
                        listOf(
                          MaterialTheme.customColors.taskBgGradientColors[0][0],
                          MaterialTheme.customColors.taskBgGradientColors[0][1],
                        )
                    ),
                )
                Spacer(modifier = Modifier.width(16.dp))
                Box(modifier = Modifier.weight(1f))
              }
            }
          }
        },
        gesturesEnabled = drawerState.isOpen,
      ) {
        Scaffold(
          containerColor = MaterialTheme.colorScheme.background,
          topBar = {
            // Top bar fades in and slides down on first frame.
            val progress =
              if (!enableAnimation) 1f
              else
                rememberDelayedAnimationProgress(
                  initialDelay = ANIMATION_INIT_DELAY - 50,
                  animationDurationMs = TOP_APP_BAR_ANIMATION_DURATION,
                  animationLabel = "top bar",
                )
            Box(
              modifier =
                Modifier.graphicsLayer {
                  alpha = progress
                  translationY = ((-16).dp * (1 - progress)).toPx()
                }
            ) {
              GalleryTopAppBar(
                title = stringResource(HomeScreenDestination.titleRes),
                leftAction =
                  AppBarAction(
                    actionType = AppBarActionType.MENU,
                    actionFn = {
                      scope.launch { drawerState.apply { if (isClosed) open() else close() } }
                    },
                  ),
              )
            }
          },
        ) { innerPadding ->
          Box(
            contentAlignment = Alignment.TopCenter,
            modifier =
              Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
          ) {
            Box(
              contentAlignment = Alignment.TopCenter,
              modifier =
                Modifier.fillMaxSize()
                  .padding(top = innerPadding.calculateTopPadding())
                  .verticalScroll(rememberScrollState()),
            ) {
              Column(modifier = Modifier.fillMaxWidth()) {
                // Husk hub: greeting + wordmark + intro text + primary "Talk" card +
                // secondary task grid + model status pill. The drawer, top bar, and
                // notification permission flow above this point are unchanged from Gallery.
                Column(
                  modifier =
                    Modifier.padding(horizontal = 24.dp)
                      .padding(top = 24.dp, bottom = 24.dp)
                      .semantics(mergeDescendants = true) {},
                  verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  HuskGreeting(enableAnimation = enableAnimation)
                  AppTitleGm4(enableAnimation = enableAnimation)
                  HuskIntroText(enableAnimation = enableAnimation)
                }

                HuskHub(
                  modelManagerViewModel = modelManagerViewModel,
                  notesViewModel = notesViewModel,
                  tasks = tasks,
                  enableAnimation = enableAnimation,
                  navigateToTaskScreen = navigateToTaskScreen,
                  onModelsClicked = onModelsClicked,
                  onNotesClicked = onNotesClicked,
                  onNotesSearchClicked = onNotesSearchClicked,
                  onNoteClicked = onNoteClicked,
                )

                Spacer(
                  modifier = Modifier.height(innerPadding.calculateBottomPadding() + 24.dp)
                )
              }
            }

            // Gradient overlay at the bottom — fades the scroll into the background color.
            Box(
              modifier =
                Modifier.fillMaxWidth()
                  .height(innerPadding.calculateBottomPadding())
                  .background(
                    Brush.verticalGradient(
                      colors = listOf(Color.Transparent, MaterialTheme.colorScheme.background)
                    )
                  )
                  .align(Alignment.BottomCenter)
            )
          }
        }
      }
    }
  }

  // Show TOS dialog for users to accept.
  if (showTosDialog) {
    AppTosDialog(
      onTosAccepted = {
        showTosDialog = false
        tosViewModel.acceptTos()
      }
    )
  }

  // Settings dialog.
  if (showSettingsDialog) {
    SettingsDialog(
      curThemeOverride = modelManagerViewModel.readThemeOverride(),
      modelManagerViewModel = modelManagerViewModel,
      onDismissed = { showSettingsDialog = false },
    )
  }

  if (uiState.loadingModelAllowlistError.isNotEmpty()) {
    AlertDialog(
      icon = {
        Icon(
          Icons.Rounded.Error,
          contentDescription = stringResource(R.string.cd_error),
          tint = MaterialTheme.colorScheme.error,
        )
      },
      title = { Text(uiState.loadingModelAllowlistError) },
      text = { Text("Please check your internet connection and try again later.") },
      onDismissRequest = { modelManagerViewModel.loadModelAllowlist() },
      confirmButton = {
        TextButton(onClick = { modelManagerViewModel.loadModelAllowlist() }) { Text("Retry") }
      },
      dismissButton = {
        TextButton(onClick = { modelManagerViewModel.clearLoadModelAllowlistError() }) {
          Text("Cancel")
        }
      },
    )
  }
}

@Composable
fun AppTitleGm4(enableAnimation: Boolean) {
  val text1 = stringResource(R.string.app_name_first_part)
  val text2 = stringResource(R.string.app_name_second_part)
  val annotatedText = buildAnnotatedString {
    withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.onSurface)) { append(text1) }
    withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.primary)) { append(text2) }
  }

  RevealingText(
    text = "",
    annotatedText = annotatedText,
    style =
      MaterialTheme.typography.displayMedium.copy(
        fontWeight = FontWeight.Medium,
        fontSize = 48.sp,
        lineHeight = 52.sp,
      ),
    animationDelay = 0,
    animationDurationMs =
      if (enableAnimation) {
        (TITLE_FIRST_LINE_ANIMATION_DURATION + TITLE_SECOND_LINE_ANIMATION_DURATION)
      } else {
        0
      },
    extraTextPadding = 0.dp,
  )
}

@Composable
private fun HuskGreeting(enableAnimation: Boolean) {
  val greeting =
    when (LocalTime.now().hour) {
      in 5..11 -> "Good morning."
      in 12..17 -> "Good afternoon."
      in 18..21 -> "Good evening."
      else -> "Still here."
    }
  val progress =
    if (!enableAnimation) 1f
    else
      rememberDelayedAnimationProgress(
        initialDelay = ANIMATION_INIT_DELAY,
        animationDurationMs = CONTENT_COMPOSABLES_ANIMATION_DURATION,
        animationLabel = "husk greeting",
      )
  Text(
    greeting,
    style = MaterialTheme.typography.titleMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier =
      Modifier.graphicsLayer {
        alpha = progress
        translationY = (CONTENT_COMPOSABLES_OFFSET_Y.dp * (1 - progress)).toPx()
      },
  )
}

@Composable
private fun HuskIntroText(enableAnimation: Boolean) {
  val progress =
    if (!enableAnimation) 1f
    else
      rememberDelayedAnimationProgress(
        initialDelay = TITLE_SECOND_LINE_ANIMATION_START,
        animationDurationMs = CONTENT_COMPOSABLES_ANIMATION_DURATION,
        animationLabel = "husk intro text",
      )
  Text(
    stringResource(R.string.app_intro),
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier =
      Modifier.padding(top = 12.dp).graphicsLayer {
        alpha = progress
        translationY = (CONTENT_COMPOSABLES_OFFSET_Y.dp * (1 - progress)).toPx()
      },
  )
}

@Composable
private fun HuskHub(
  modelManagerViewModel: ModelManagerViewModel,
  notesViewModel: NotesViewModel,
  tasks: List<Task>,
  enableAnimation: Boolean,
  navigateToTaskScreen: (Task) -> Unit,
  onModelsClicked: () -> Unit,
  onNotesClicked: () -> Unit,
  onNotesSearchClicked: () -> Unit,
  onNoteClicked: (noteId: String) -> Unit,
) {
  val talkTask = remember(tasks) { tasks.firstOrNull { it.id == BuiltInTaskId.LLM_CHAT } }
  val secondaryTasks =
    remember(tasks) {
      // Everything except the primary "Talk" task. Lets all existing surfaces (Tinker,
      // Look, Listen, Mobile Actions, Agent Skills, Tiny Garden, etc.) stay reachable
      // from the hub without enumerating them — additive by construction.
      tasks.filter { it.id != BuiltInTaskId.LLM_CHAT }
    }

  val progress =
    if (!enableAnimation) 1f
    else
      rememberDelayedAnimationProgress(
        initialDelay = TASK_LIST_ANIMATION_START,
        animationDurationMs = CONTENT_COMPOSABLES_ANIMATION_DURATION,
        animationLabel = "husk hub",
      )

  Column(
    modifier =
      Modifier.fillMaxWidth().padding(horizontal = 24.dp).graphicsLayer {
        alpha = progress
        translationY = (CONTENT_COMPOSABLES_OFFSET_Y.dp * (1 - progress)).toPx()
      },
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    if (talkTask != null) {
      HuskTalkCard(task = talkTask, onClick = { navigateToTaskScreen(talkTask) })
    }

    HuskNotesCard(
      viewModel = notesViewModel,
      onCardClick = onNotesClicked,
      onSearchClick = onNotesSearchClicked,
      onNoteClick = onNoteClicked,
    )

    HuskSecondaryGrid(
      tasks = secondaryTasks,
      onTaskClick = navigateToTaskScreen,
      onModelsClick = onModelsClicked,
    )

    HuskModelStatusPill(modelManagerViewModel = modelManagerViewModel)
  }
}

@Composable
private fun HuskTalkCard(task: Task, onClick: () -> Unit) {
  val cd = stringResource(R.string.cd_task_card, task.label, task.models.size)
  Card(
    modifier =
      Modifier.fillMaxWidth()
        .height(140.dp)
        .clip(RoundedCornerShape(28.dp))
        .clickable(onClick = onClick)
        .semantics { contentDescription = cd },
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.customColors.userBubbleBgColor),
  ) {
    Column(
      modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 24.dp),
      verticalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(
        task.label,
        color = MaterialTheme.colorScheme.background,
        style =
          MaterialTheme.typography.headlineLarge.copy(
            fontWeight = FontWeight.Medium,
            fontSize = 36.sp,
            lineHeight = 40.sp,
          ),
      )
      Text(
        task.shortDescription,
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.75f),
        style = MaterialTheme.typography.bodyMedium,
      )
    }
  }
}

@Composable
private fun HuskSecondaryGrid(
  tasks: List<Task>,
  onTaskClick: (Task) -> Unit,
  onModelsClick: () -> Unit,
) {
  // 2-column grid: every secondary task as a card, plus a synthetic "Models" card at
  // the end that opens the model manager (which is not itself a Task instance).
  val cells: List<HuskHubCell> = buildList {
    tasks.forEach { add(HuskHubCell.TaskCell(it)) }
    add(HuskHubCell.ModelsCell)
  }
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    for (i in cells.indices step 2) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        HuskSecondaryCell(cell = cells[i], onTaskClick = onTaskClick, onModelsClick = onModelsClick, modifier = Modifier.weight(1f))
        if (i + 1 < cells.size) {
          HuskSecondaryCell(
            cell = cells[i + 1],
            onTaskClick = onTaskClick,
            onModelsClick = onModelsClick,
            modifier = Modifier.weight(1f),
          )
        } else {
          Spacer(modifier = Modifier.weight(1f))
        }
      }
    }
  }
}

private sealed class HuskHubCell {
  data class TaskCell(val task: Task) : HuskHubCell()
  object ModelsCell : HuskHubCell()
}

@Composable
private fun HuskSecondaryCell(
  cell: HuskHubCell,
  onTaskClick: (Task) -> Unit,
  onModelsClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  when (cell) {
    is HuskHubCell.TaskCell -> {
      val task = cell.task
      val cd = stringResource(R.string.cd_task_card, task.label, task.models.size)
      Card(
        modifier =
          modifier
            .height(120.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable { onTaskClick(task) }
            .border(
              width = 1.dp,
              color = MaterialTheme.colorScheme.outline,
              shape = RoundedCornerShape(20.dp),
            )
            .semantics { contentDescription = cd },
        colors =
          CardDefaults.cardColors(containerColor = MaterialTheme.customColors.taskCardBgColor),
      ) {
        Column(
          modifier = Modifier.fillMaxSize().padding(16.dp),
          verticalArrangement = Arrangement.SpaceBetween,
        ) {
          TaskIcon(task = task, width = 32.dp)
          Text(
            task.label,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
          )
        }
      }
    }
    HuskHubCell.ModelsCell -> {
      Card(
        modifier =
          modifier
            .height(120.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable { onModelsClick() }
            .border(
              width = 1.dp,
              color = MaterialTheme.colorScheme.outline,
              shape = RoundedCornerShape(20.dp),
            ),
        colors =
          CardDefaults.cardColors(containerColor = MaterialTheme.customColors.taskCardBgColor),
      ) {
        Column(
          modifier = Modifier.fillMaxSize().padding(16.dp),
          verticalArrangement = Arrangement.SpaceBetween,
        ) {
          Icon(
            Icons.AutoMirrored.Rounded.ListAlt,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(32.dp),
          )
          Text(
            stringResource(R.string.drawer_models_label),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
          )
        }
      }
    }
  }
}

@Composable
private fun HuskModelStatusPill(modelManagerViewModel: ModelManagerViewModel) {
  val uiState by modelManagerViewModel.uiState.collectAsState()
  val loadedModelLabel =
    remember(uiState.tasks) {
      val first =
        uiState.tasks
          .asSequence()
          .flatMap { it.models.asSequence() }
          .firstOrNull { it.instance != null }
      first?.name ?: "No model loaded"
    }

  Row(
    modifier =
      Modifier.fillMaxWidth()
        .padding(top = 8.dp)
        .clip(CircleShape)
        .border(
          width = 1.dp,
          color = MaterialTheme.colorScheme.outlineVariant,
          shape = CircleShape,
        )
        .padding(horizontal = 16.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Box(
      modifier =
        Modifier.size(8.dp)
          .clip(CircleShape)
          .background(
            if (loadedModelLabel == "No model loaded") MaterialTheme.colorScheme.outline
            else MaterialTheme.colorScheme.primary
          )
    )
    Text(
      loadedModelLabel,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      style = MaterialTheme.typography.bodySmall,
    )
  }
}
