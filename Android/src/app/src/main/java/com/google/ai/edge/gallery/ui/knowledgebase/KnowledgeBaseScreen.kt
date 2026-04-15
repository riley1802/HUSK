package com.google.ai.edge.gallery.ui.knowledgebase

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.ai.edge.gallery.data.rag.Document
import com.google.ai.edge.gallery.data.rag.DocumentChunk
import com.google.ai.edge.gallery.data.rag.IngestionStatus
import com.google.ai.edge.gallery.ui.theme.customColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnowledgeBaseScreen(
	viewModel: KnowledgeBaseViewModel = hiltViewModel(),
	navigateUp: () -> Unit,
) {
	val documents by viewModel.documents.collectAsState()
	val stats by viewModel.stats.collectAsState()
	val selectedDocument by viewModel.selectedDocument.collectAsState()
	val selectedChunks by viewModel.selectedDocumentChunks.collectAsState()
	val context = LocalContext.current

	val filePicker = rememberLauncherForActivityResult(
		contract = ActivityResultContracts.OpenDocument(),
	) { uri: Uri? ->
		uri?.let {
			val name = uri.lastPathSegment?.substringAfterLast('/') ?: "document"
			val mimeType = context.contentResolver.getType(uri) ?: "text/plain"
			// Take persistable read permission so we can read the file later
			context.contentResolver.takePersistableUriPermission(
				uri,
				android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
			)
			viewModel.ingestDocument(context, uri, name, mimeType)
		}
	}

	AnimatedContent(
		targetState = selectedDocument,
		label = "kb_nav",
	) { doc ->
		if (doc != null) {
			DocumentDetailScreen(
				document = doc,
				chunks = selectedChunks,
				onBack = { viewModel.clearSelectedDocument() },
				onDelete = { viewModel.deleteDocument(doc.id) },
			)
		} else {
			KnowledgeBaseListScreen(
				documents = documents,
				stats = stats,
				navigateUp = navigateUp,
				onAddDocument = {
					filePicker.launch(arrayOf(
						"text/plain",
						"text/markdown",
						"text/x-markdown",
						"application/pdf",
					))
				},
				onDocumentClicked = { viewModel.selectDocument(it) },
			)
		}
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KnowledgeBaseListScreen(
	documents: List<Document>,
	stats: KnowledgeBaseStats,
	navigateUp: () -> Unit,
	onAddDocument: () -> Unit,
	onDocumentClicked: (Document) -> Unit,
) {
	val customColors = MaterialTheme.customColors

	Scaffold(
		topBar = {
			TopAppBar(
				title = { Text("Knowledge Base") },
				navigationIcon = {
					IconButton(onClick = navigateUp) {
						Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
					}
				},
				colors = TopAppBarDefaults.topAppBarColors(
					containerColor = MaterialTheme.colorScheme.background,
				),
			)
		},
	) { padding ->
		LazyColumn(
			modifier = Modifier
				.fillMaxSize()
				.padding(padding)
				.padding(horizontal = 24.dp),
			verticalArrangement = Arrangement.spacedBy(8.dp),
		) {
			// Stats bar
			item {
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.clip(RoundedCornerShape(12.dp))
						.background(MaterialTheme.colorScheme.surfaceContainerHigh)
						.padding(12.dp, 12.dp),
					horizontalArrangement = Arrangement.spacedBy(16.dp),
				) {
					Text(
						"${stats.documentCount} docs",
						fontSize = 12.sp,
						fontWeight = FontWeight.SemiBold,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
					)
					Text("|", fontSize = 12.sp, color = MaterialTheme.colorScheme.outlineVariant)
					Text(
						"${stats.chunkCount} chunks",
						fontSize = 12.sp,
						fontWeight = FontWeight.SemiBold,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
					)
					Text("|", fontSize = 12.sp, color = MaterialTheme.colorScheme.outlineVariant)
					Text(
						"${"%.1f".format(stats.vectorSizeBytes / (1024.0 * 1024.0))} MB",
						fontSize = 12.sp,
						fontWeight = FontWeight.SemiBold,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
					)
					Text("|", fontSize = 12.sp, color = MaterialTheme.colorScheme.outlineVariant)
					Text(
						stats.embeddingModel,
						fontSize = 12.sp,
						fontWeight = FontWeight.SemiBold,
						color = customColors.successColor,
					)
				}
				Spacer(modifier = Modifier.height(8.dp))
			}

			if (documents.isEmpty()) {
				item {
					Box(
						modifier = Modifier
							.fillMaxWidth()
							.padding(vertical = 64.dp),
						contentAlignment = Alignment.Center,
					) {
						Column(horizontalAlignment = Alignment.CenterHorizontally) {
							Text(
								"No documents yet",
								style = MaterialTheme.typography.titleMedium,
								color = MaterialTheme.colorScheme.onSurfaceVariant,
							)
							Spacer(modifier = Modifier.height(8.dp))
							Text(
								"Add documents to build your knowledge base",
								fontSize = 14.sp,
								color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
							)
						}
					}
				}
			}

			items(documents, key = { it.id }) { document ->
				DocumentCard(
					document = document,
					onClick = { onDocumentClicked(document) },
				)
			}

			// Add document button
			item {
				Spacer(modifier = Modifier.height(8.dp))
				Button(
					onClick = onAddDocument,
					modifier = Modifier.fillMaxWidth(),
					shape = RoundedCornerShape(12.dp),
					colors = ButtonDefaults.buttonColors(
						containerColor = MaterialTheme.colorScheme.primary,
					),
				) {
					Icon(Icons.Rounded.Add, contentDescription = null)
					Text("  Add Document", fontWeight = FontWeight.Bold)
				}
				Spacer(modifier = Modifier.height(24.dp))
			}
		}
	}
}

@Composable
private fun DocumentCard(
	document: Document,
	onClick: () -> Unit,
) {
	val customColors = MaterialTheme.customColors

	Column(
		modifier = Modifier
			.fillMaxWidth()
			.clip(RoundedCornerShape(12.dp))
			.background(MaterialTheme.colorScheme.surfaceContainer)
			.clickable(onClick = onClick)
			.padding(16.dp),
	) {
		Row(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.SpaceBetween,
			verticalAlignment = Alignment.Top,
		) {
			Column(modifier = Modifier.weight(1f)) {
				Text(
					text = document.name,
					fontSize = 15.sp,
					fontWeight = FontWeight.SemiBold,
					color = MaterialTheme.colorScheme.onSurface,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
				Spacer(modifier = Modifier.height(4.dp))
				val subtitle = when (document.status) {
					IngestionStatus.READY ->
						"${document.chunkCount} chunks · ${"%.0f".format(document.vectorSizeBytes / 1024.0)} KB vectors"
					IngestionStatus.PROCESSING -> "Processing..."
					IngestionStatus.FAILED -> document.errorMessage ?: "Ingestion failed"
					IngestionStatus.PENDING -> "Pending..."
				}
				Text(
					text = subtitle,
					fontSize = 12.sp,
					color = when (document.status) {
						IngestionStatus.FAILED -> customColors.errorTextColor
						else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
					},
				)
			}

			val statusColor = when (document.status) {
				IngestionStatus.READY -> customColors.successColor
				IngestionStatus.PROCESSING -> customColors.warningTextColor
				IngestionStatus.FAILED -> customColors.errorTextColor
				IngestionStatus.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
			}
			val statusLabel = when (document.status) {
				IngestionStatus.READY -> "Ready"
				IngestionStatus.PROCESSING -> "Indexing"
				IngestionStatus.FAILED -> "Failed"
				IngestionStatus.PENDING -> "Pending"
			}
			Row(
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(4.dp),
			) {
				Text("●", fontSize = 8.sp, color = statusColor)
				Text(
					statusLabel,
					fontSize = 11.sp,
					fontWeight = FontWeight.Bold,
					letterSpacing = 0.5.sp,
					color = statusColor,
				)
			}
		}

		// Progress bar for processing
		if (document.status == IngestionStatus.PROCESSING) {
			Spacer(modifier = Modifier.height(10.dp))
			LinearProgressIndicator(
				modifier = Modifier
					.fillMaxWidth()
					.height(3.dp)
					.clip(RoundedCornerShape(2.dp)),
				color = MaterialTheme.colorScheme.primary,
				trackColor = MaterialTheme.colorScheme.outlineVariant,
			)
		}
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DocumentDetailScreen(
	document: Document,
	chunks: List<DocumentChunk>,
	onBack: () -> Unit,
	onDelete: () -> Unit,
) {
	var showDeleteDialog by remember { mutableStateOf(false) }
	val customColors = MaterialTheme.customColors

	if (showDeleteDialog) {
		AlertDialog(
			onDismissRequest = { showDeleteDialog = false },
			title = { Text("Delete Document") },
			text = { Text("Delete '${document.name}' and all its indexed data?") },
			confirmButton = {
				TextButton(onClick = {
					showDeleteDialog = false
					onDelete()
				}) {
					Text("Delete", color = customColors.errorTextColor)
				}
			},
			dismissButton = {
				TextButton(onClick = { showDeleteDialog = false }) {
					Text("Cancel")
				}
			},
		)
	}

	Scaffold(
		topBar = {
			TopAppBar(
				title = {
					Text(
						document.name,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
					)
				},
				navigationIcon = {
					IconButton(onClick = onBack) {
						Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
					}
				},
				actions = {
					IconButton(onClick = { showDeleteDialog = true }) {
						Icon(
							Icons.Rounded.Delete,
							contentDescription = "Delete",
							tint = customColors.errorTextColor,
						)
					}
				},
				colors = TopAppBarDefaults.topAppBarColors(
					containerColor = MaterialTheme.colorScheme.background,
				),
			)
		},
	) { padding ->
		LazyColumn(
			modifier = Modifier
				.fillMaxSize()
				.padding(padding)
				.padding(horizontal = 24.dp),
			verticalArrangement = Arrangement.spacedBy(8.dp),
		) {
			// Metadata card
			item {
				Column(
					modifier = Modifier
						.fillMaxWidth()
						.clip(RoundedCornerShape(12.dp))
						.background(MaterialTheme.colorScheme.surfaceContainer)
						.padding(16.dp),
				) {
					val metadata = listOf(
						"Type" to document.mimeType.substringAfter('/').uppercase(),
						"Chunks" to document.chunkCount.toString(),
						"Vector Size" to "${"%.0f".format(document.vectorSizeBytes / 1024.0)} KB",
						"Embedding" to (document.embeddingModel ?: "Unknown"),
						"Collection" to document.collectionId.replaceFirstChar { it.uppercase() },
						"Status" to document.status.name,
					)

					val rows = metadata.chunked(2)
					rows.forEach { row ->
						Row(
							modifier = Modifier
								.fillMaxWidth()
								.padding(vertical = 7.dp),
						) {
							row.forEach { (label, value) ->
								Column(modifier = Modifier.weight(1f)) {
									Text(
										label.uppercase(),
										fontSize = 11.sp,
										fontWeight = FontWeight.Bold,
										letterSpacing = 0.5.sp,
										color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
									)
									Spacer(modifier = Modifier.height(2.dp))
									Text(
										value,
										fontSize = 13.sp,
										color = if (label == "Embedding") {
											customColors.linkColor
										} else if (label == "Status" && document.status == IngestionStatus.READY) {
											customColors.successColor
										} else {
											MaterialTheme.colorScheme.onSurface
										},
									)
								}
							}
						}
					}
				}
			}

			// Chunks section header
			item {
				Spacer(modifier = Modifier.height(8.dp))
				Text(
					"CHUNKS",
					fontSize = 14.sp,
					fontWeight = FontWeight.Bold,
					letterSpacing = 0.5.sp,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
			}

			// Chunk cards
			items(chunks) { chunk ->
				Column(
					modifier = Modifier
						.fillMaxWidth()
						.clip(RoundedCornerShape(12.dp))
						.background(MaterialTheme.colorScheme.surfaceContainerHigh)
						.padding(14.dp),
				) {
					Text(
						"${chunk.chunkIndex + 1} / ${document.chunkCount}",
						fontSize = 11.sp,
						fontWeight = FontWeight.Bold,
						color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
					)
					Spacer(modifier = Modifier.height(6.dp))
					Text(
						chunk.content,
						fontSize = 13.sp,
						lineHeight = 20.sp,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						maxLines = 6,
						overflow = TextOverflow.Ellipsis,
					)
				}
			}

			item { Spacer(modifier = Modifier.height(24.dp)) }
		}
	}
}
