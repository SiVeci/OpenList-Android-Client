package io.openlist.client.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PreviewKindResolverTest {

    @Test
    fun `classifies common image extensions`() {
        assertEquals(PreviewKind.IMAGE, PreviewKindResolver.resolve("photo.jpg"))
        assertEquals(PreviewKind.IMAGE, PreviewKindResolver.resolve("photo.jpeg"))
        assertEquals(PreviewKind.IMAGE, PreviewKindResolver.resolve("photo.png"))
        assertEquals(PreviewKind.IMAGE, PreviewKindResolver.resolve("photo.gif"))
        assertEquals(PreviewKind.IMAGE, PreviewKindResolver.resolve("photo.webp"))
        assertEquals(PreviewKind.IMAGE, PreviewKindResolver.resolve("photo.bmp"))
        assertEquals(PreviewKind.IMAGE, PreviewKindResolver.resolve("photo.heic"))
    }

    @Test
    fun `classifies common text and code extensions, excluding markdown`() {
        assertEquals(PreviewKind.TEXT, PreviewKindResolver.resolve("notes.txt"))
        assertEquals(PreviewKind.TEXT, PreviewKindResolver.resolve("data.json"))
        assertEquals(PreviewKind.TEXT, PreviewKindResolver.resolve("config.xml"))
        assertEquals(PreviewKind.TEXT, PreviewKindResolver.resolve("config.yaml"))
        assertEquals(PreviewKind.TEXT, PreviewKindResolver.resolve("config.yml"))
        assertEquals(PreviewKind.TEXT, PreviewKindResolver.resolve("app.log"))
        assertEquals(PreviewKind.TEXT, PreviewKindResolver.resolve("table.csv"))
        assertEquals(PreviewKind.TEXT, PreviewKindResolver.resolve("Main.kt"))
        assertEquals(PreviewKind.TEXT, PreviewKindResolver.resolve("script.py"))
        // .md must classify as MARKDOWN, never TEXT, even though it's plain text.
        assertEquals(PreviewKind.MARKDOWN, PreviewKindResolver.resolve("README.md"))
    }

    @Test
    fun `classifies markdown extensions`() {
        assertEquals(PreviewKind.MARKDOWN, PreviewKindResolver.resolve("README.md"))
        assertEquals(PreviewKind.MARKDOWN, PreviewKindResolver.resolve("doc.markdown"))
    }

    @Test
    fun `classifies common video extensions`() {
        assertEquals(PreviewKind.VIDEO, PreviewKindResolver.resolve("movie.mp4"))
        assertEquals(PreviewKind.VIDEO, PreviewKindResolver.resolve("movie.mkv"))
        assertEquals(PreviewKind.VIDEO, PreviewKindResolver.resolve("movie.avi"))
        assertEquals(PreviewKind.VIDEO, PreviewKindResolver.resolve("movie.mov"))
        assertEquals(PreviewKind.VIDEO, PreviewKindResolver.resolve("movie.webm"))
        // Regression: .ts is an MPEG transport stream (video), not TypeScript
        // source — it must never fall into TEXT_EXTENSIONS.
        assertEquals(PreviewKind.VIDEO, PreviewKindResolver.resolve("clip.ts"))
    }

    @Test
    fun `classifies common audio extensions`() {
        assertEquals(PreviewKind.AUDIO, PreviewKindResolver.resolve("song.mp3"))
        assertEquals(PreviewKind.AUDIO, PreviewKindResolver.resolve("song.flac"))
        assertEquals(PreviewKind.AUDIO, PreviewKindResolver.resolve("song.wav"))
        assertEquals(PreviewKind.AUDIO, PreviewKindResolver.resolve("song.aac"))
        assertEquals(PreviewKind.AUDIO, PreviewKindResolver.resolve("song.m4a"))
    }

    @Test
    fun `classifies pdf extension`() {
        assertEquals(PreviewKind.PDF, PreviewKindResolver.resolve("report.pdf"))
    }

    @Test
    fun `classifies common office document extensions`() {
        assertEquals(PreviewKind.OFFICE, PreviewKindResolver.resolve("report.doc"))
        assertEquals(PreviewKind.OFFICE, PreviewKindResolver.resolve("report.docx"))
        assertEquals(PreviewKind.OFFICE, PreviewKindResolver.resolve("sheet.xls"))
        assertEquals(PreviewKind.OFFICE, PreviewKindResolver.resolve("sheet.xlsx"))
        assertEquals(PreviewKind.OFFICE, PreviewKindResolver.resolve("deck.ppt"))
        assertEquals(PreviewKind.OFFICE, PreviewKindResolver.resolve("deck.pptx"))
    }

    @Test
    fun `returns UNKNOWN for no extension or an unrecognized extension`() {
        assertEquals(PreviewKind.UNKNOWN, PreviewKindResolver.resolve("README"))
        assertEquals(PreviewKind.UNKNOWN, PreviewKindResolver.resolve("archive.zip"))
        assertEquals(PreviewKind.UNKNOWN, PreviewKindResolver.resolve("binary.exe"))
    }

    @Test
    fun `extension matching is case-insensitive`() {
        assertEquals(PreviewKind.IMAGE, PreviewKindResolver.resolve("PHOTO.JPG"))
        assertEquals(PreviewKind.VIDEO, PreviewKindResolver.resolve("MOVIE.MP4"))
        assertEquals(PreviewKind.MARKDOWN, PreviewKindResolver.resolve("README.MD"))
    }
}
