package io.openlist.client.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.openlist.client.core.designsystem.OpenListPalette
import io.openlist.client.core.designsystem.OpenListTheme
import io.openlist.client.core.designsystem.Spacing

enum class FileKind { FOLDER, IMAGE, VIDEO, AUDIO, DOCUMENT, ARCHIVE, CODE, OTHER }

fun fileKindOf(name: String, isDir: Boolean): FileKind {
    if (isDir) return FileKind.FOLDER
    return when (name.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "heic", "heif", "avif" -> FileKind.IMAGE
        "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v", "ts", "rmvb" -> FileKind.VIDEO
        "mp3", "flac", "wav", "aac", "ogg", "m4a", "wma", "opus", "ape" -> FileKind.AUDIO
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md", "epub", "csv" -> FileKind.DOCUMENT
        "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "iso" -> FileKind.ARCHIVE
        "kt", "java", "py", "js", "ts", "json", "xml", "yaml", "yml", "html", "css", "sh", "c", "cpp", "h", "go", "rs" -> FileKind.CODE
        else -> FileKind.OTHER
    }
}

data class PastelStyle(val container: Color, val content: Color)

/** Light theme renders the DESIGN.md badge-tag pattern verbatim (pastel tint
 * plate + deep same-hue ink); dark theme swaps the roles — deep ink glazed
 * onto the surface as the plate, pastel tint as the content. */
@Composable
fun pastelStyle(tint: Color, deep: Color): PastelStyle =
    if (OpenListTheme.isDark) PastelStyle(deep.copy(alpha = 0.32f), tint) else PastelStyle(tint, deep)

@Composable
fun fileKindStyle(kind: FileKind): PastelStyle = when (kind) {
    FileKind.FOLDER -> pastelStyle(OpenListPalette.TintLavender, OpenListPalette.BrandPurple800)
    FileKind.IMAGE -> pastelStyle(OpenListPalette.TintMint, OpenListPalette.BrandGreen)
    FileKind.VIDEO -> pastelStyle(OpenListPalette.TintPeach, OpenListPalette.BrandOrangeDeep)
    FileKind.AUDIO -> pastelStyle(OpenListPalette.TintRose, OpenListPalette.BrandPinkDeep)
    FileKind.DOCUMENT -> pastelStyle(OpenListPalette.TintSky, OpenListPalette.LinkBlueDeep)
    FileKind.ARCHIVE -> pastelStyle(OpenListPalette.TintYellow, OpenListPalette.BrandBrown)
    FileKind.CODE -> pastelStyle(OpenListPalette.TintGray, OpenListPalette.Charcoal)
    FileKind.OTHER -> pastelStyle(OpenListPalette.TintGray, OpenListPalette.Slate)
}

fun fileKindIcon(kind: FileKind): ImageVector = when (kind) {
    FileKind.FOLDER -> Icons.Outlined.Folder
    FileKind.IMAGE -> Icons.Outlined.Image
    FileKind.VIDEO -> Icons.Outlined.Movie
    FileKind.AUDIO -> Icons.Outlined.MusicNote
    FileKind.DOCUMENT -> Icons.Outlined.Description
    FileKind.ARCHIVE -> Icons.Outlined.FolderZip
    FileKind.CODE -> Icons.Outlined.Code
    FileKind.OTHER -> Icons.Outlined.InsertDriveFile
}

/** Pastel rounded plate ({rounded.md}) carrying the file-type icon — the
 * DESIGN.md "database property" color echo applied to file rows. */
@Composable
fun FileTypeIconPlate(
    kind: FileKind,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val style = fileKindStyle(kind)
    Box(
        modifier = modifier
            .size(size)
            .background(style.container, MaterialTheme.shapes.medium),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = fileKindIcon(kind),
            contentDescription = null,
            modifier = Modifier.size(size * 0.55f),
            tint = style.content,
        )
    }
}

/** Small tag chip per DESIGN.md badge-tag-*: {rounded.sm}, caption-bold, tint
 * background + deep text. Used for file extension / kind labels. */
@Composable
fun FileTypeBadge(
    text: String,
    kind: FileKind,
    modifier: Modifier = Modifier,
) {
    val style = fileKindStyle(kind)
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = style.content,
        modifier = modifier
            .background(style.container, MaterialTheme.shapes.small)
            .padding(horizontal = Spacing.xs, vertical = 2.dp),
    )
}
