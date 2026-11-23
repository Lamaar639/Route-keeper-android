package com.example.routekeeper.data.service

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class PdfExtractionResult(
    val fileName: String,
    val extractedText: String,
    val pageCount: Int,
    val usedOcrFallback: Boolean = false
)

object PdfManifestExtractor {

    @Volatile
    private var pdfBoxInitialized = false

    private fun ensurePdfBoxInitialized(appContext: Context) {
        if (!pdfBoxInitialized) {
            synchronized(this) {
                if (!pdfBoxInitialized) {
                    PDFBoxResourceLoader.init(appContext)
                    pdfBoxInitialized = true
                }
            }
        }
    }

    suspend fun extractFromUri(context: Context, uri: Uri): PdfExtractionResult = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        ensurePdfBoxInitialized(appContext)

        val displayName = resolveDisplayName(appContext, uri)
        val tempPdf = File.createTempFile("shared_manifest_", ".pdf", appContext.cacheDir)

        try {
            val copiedBytes = try {
                appContext.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempPdf).use { output ->
                        input.copyTo(output)
                    }
                } ?: throw IllegalStateException("Unable to open shared PDF stream. Make sure the source app granted read access.")
            } catch (_: SecurityException) {
                throw IllegalStateException("Permission denied reading \"$displayName\". Try sharing it again from your PDF viewer.")
            }

            if (copiedBytes <= 0L || tempPdf.length() <= 0L) {
                throw IllegalStateException("The shared PDF file appears to be empty.")
            }

            var pageCount = 0
            var extractedText = ""

            try {
                PDDocument.load(tempPdf).use { doc ->
                    pageCount = doc.numberOfPages
                    val stripper = PDFTextStripper().apply {
                        sortByPosition = true
                    }
                    extractedText = stripper.getText(doc).orEmpty()
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: InvalidPasswordException) {
                throw IllegalStateException("\"$displayName\" is password-protected and cannot be auto-imported.")
            }

            val normalizedText = normalizePdfText(extractedText)
            if (normalizedText.isBlank()) {
                throw IllegalStateException("No selectable text was found in \"$displayName\".")
            }

            PdfExtractionResult(
                fileName = displayName,
                extractedText = normalizedText,
                pageCount = pageCount.coerceAtLeast(1),
                usedOcrFallback = false
            )
        } finally {
            tempPdf.delete()
        }
    }

    private fun resolveDisplayName(context: Context, uri: Uri): String {
        if (uri.scheme == "content") {
            runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) {
                            val name = cursor.getString(idx)
                            if (!name.isNullOrBlank()) return name
                        }
                    }
                }
            }
        }
        val segment = uri.lastPathSegment?.substringAfterLast('/')
        return if (!segment.isNullOrBlank()) segment else "Shared Delivery PDF"
    }

    private fun normalizePdfText(raw: String): String {
        return raw
            .replace("\u00A0", " ")
            .replace("\u200B", "")
            .replace("\uFEFF", "")
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .replace(Regex("""[ \t]+\n"""), "\n")
            .replace(Regex("""\n{3,}"""), "\n\n")
            .trim()
    }
}
