package com.example.ui.components

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Image as ImageIcon
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.data.i18n.AppStrings
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.GlassBorderWhite
import com.example.ui.theme.StatusCancelRed

data class SelectedFileItem(
    val uri: Uri,
    val name: String,
    val originalFormat: String = "",
    val hasTransparency: Boolean = false,
    val thumbnailBitmap: Bitmap? = null
)

/**
 * قائمة ملفات قابلة لإعادة الترتيب.
 *
 * تدعم طريقتين لإعادة الترتيب:
 * 1. السحب والإفلات (اضغط مطولاً على مقبض السحب ثم اسحب) - سريعة ومناسبة للقوائم الطويلة.
 * 2. أزرار أعلى/أسفل - بديل يبقى متاحاً دوماً لأغراض سهولة الوصول (Accessibility)
 *    ولمن يفضل عدم استخدام إيماءات السحب.
 */
@Composable
fun ReorderableFileList(
    strings: AppStrings,
    files: List<SelectedFileItem>,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    onReorder: (fromIndex: Int, toIndex: Int) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    // ارتفاع صف واحد بالبكسل، يُقاس تلقائياً من أول عنصر ويُستخدم لحساب عتبة السحب.
    var rowHeightPx by remember { mutableIntStateOf(0) }
    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableStateOf(0f) }

    Column(modifier = modifier.fillMaxWidth()) {
        files.forEachIndexed { index, item ->
            val isDragged = draggedIndex == index
            val translation = if (isDragged) dragOffsetY else 0f

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .graphicsLayer { translationY = translation }
                    .zIndex(if (isDragged) 1f else 0f)
                    .onGloballyPositioned { coordinates ->
                        if (rowHeightPx == 0) {
                            rowHeightPx = coordinates.size.height
                        }
                    }
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0x80131822))
                    .border(1.dp, GlassBorderWhite, RoundedCornerShape(14.dp))
                    .testTag("file_item_$index"),
                color = Color(0x80131822)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Drag Handle - نقطة الالتقاط الوحيدة لإيماءة السحب، حتى لا تتعارض
                    // مع أزرار أعلى/أسفل/حذف المجاورة.
                    Icon(
                        imageVector = Icons.Default.DragHandle,
                        contentDescription = strings.dragHandleDescription,
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier
                            .size(20.dp)
                            .pointerInput(files.size) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        draggedIndex = index
                                        dragOffsetY = 0f
                                    },
                                    onDragEnd = {
                                        draggedIndex = null
                                        dragOffsetY = 0f
                                    },
                                    onDragCancel = {
                                        draggedIndex = null
                                        dragOffsetY = 0f
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        val height = rowHeightPx
                                        val currentIndex = draggedIndex
                                        if (height <= 0 || currentIndex == null) return@detectDragGesturesAfterLongPress

                                        dragOffsetY += dragAmount.y

                                        if (dragOffsetY > height / 2f && currentIndex < files.size - 1) {
                                            onReorder(currentIndex, currentIndex + 1)
                                            draggedIndex = currentIndex + 1
                                            dragOffsetY -= height
                                        } else if (dragOffsetY < -height / 2f && currentIndex > 0) {
                                            onReorder(currentIndex, currentIndex - 1)
                                            draggedIndex = currentIndex - 1
                                            dragOffsetY += height
                                        }
                                    }
                                )
                            }
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Index Badge
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(CyanPrimary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${index + 1}",
                            color = CyanPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // Thumbnail Preview
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x33FFFFFF)),
                        contentAlignment = Alignment.Center
                    ) {
                        val thumbnail = item.thumbnailBitmap
                        if (thumbnail != null && !thumbnail.isRecycled) {
                            Image(
                                bitmap = thumbnail.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            val isPdf = item.name.substringAfterLast('.', "").equals("pdf", ignoreCase = true)
                            Icon(
                                imageVector = if (isPdf) Icons.Default.PictureAsPdf else Icons.Default.ImageIcon,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = item.name,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    // Move Up
                    if (index > 0) {
                        IconButton(
                            onClick = { onMoveUp(index) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowUpward,
                                contentDescription = strings.moveUpDescription,
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Move Down
                    if (index < files.size - 1) {
                        IconButton(
                            onClick = { onMoveDown(index) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowDownward,
                                contentDescription = strings.moveDownDescription,
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Delete
                    IconButton(
                        onClick = { onDelete(index) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = strings.delete,
                            tint = StatusCancelRed,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
