package com.phantominnovation.choir

import android.content.Context
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.util.UUID

data class PdfFailure(val source: PdfSource, val message: String)
data class PdfCollectionResult(val file: File?, val included: Int, val pages: Int, val failures: List<PdfFailure>)
data class PdfProgress(val done: Int, val total: Int, val title: String)

object PdfCollection {
    const val MAX_FILES = 60
    const val MAX_FILE_BYTES = 20L * 1024 * 1024
    const val MAX_TOTAL_BYTES = 50L * 1024 * 1024
    const val MAX_PAGES = 300

    /** Only public Google files are fetched. No host token or Google credential is used. */
    internal suspend fun download(source: PdfSource, target: File, remaining: Long) {
        require(source.id != null) { "Unsupported Drive file link." }
        val suffix = source.resourceKey?.let { "&resourcekey=$it" }.orEmpty()
        val urls = if (source.document) listOf("https://docs.google.com/document/d/${source.id}/export?format=pdf$suffix")
            else listOf("https://drive.usercontent.google.com/download?id=${source.id}&export=download$suffix",
                "https://docs.google.com/document/d/${source.id}/export?format=pdf$suffix")
        var last: Exception? = null
        for (url in urls) {
            try { fetchPdf(url, target, minOf(MAX_FILE_BYTES, remaining)); return }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { last = e; target.delete() }
        }
        throw last ?: IllegalStateException("Google could not download this file.")
    }

    private suspend fun fetchPdf(url: String, target: File, limit: Long) {
        require(limit > 0) { "The collection exceeds the 50 MB limit." }
        var next = url
        for (redirect in 0..6) {
            currentCoroutineContext().ensureActive()
            val uri = URI(next)
            val host = uri.host.orEmpty()
            require(uri.scheme == "https" && (host == "drive.google.com" || host == "docs.google.com" ||
                host == "drive.usercontent.google.com" || host.endsWith(".googleusercontent.com"))) { "Google file access is unavailable. Open the Drive link to check it." }
            val connection = uri.toURL().openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000; connection.readTimeout = 30_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/pdf")
            try {
                val status = connection.responseCode
                if (status in 300..399) {
                    next = uri.resolve(connection.getHeaderField("Location") ?: error("Missing download location.")).toString()
                    continue
                }
                require(status == 200) { "File unavailable, private, or download disabled. Open its Drive link to check." }
                require(connection.contentLengthLong <= limit) { "File exceeds the PDF size limit." }
                var bytes = 0L
                connection.inputStream.use { input -> target.outputStream().use { output ->
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer); if (count < 0) break
                        bytes += count
                        require(bytes <= limit) { "File exceeds the PDF size limit." }
                        output.write(buffer, 0, count)
                    }
                } }
                val signature = target.inputStream().use { input -> val b = ByteArray(1024); val n = input.read(b); if(n > 0) String(b, 0, n, Charsets.ISO_8859_1) else "" }
                require(signature.contains("%PDF-")) { "Only downloadable PDFs and Google Docs can be combined. Check the original file." }
                return
            } finally { connection.disconnect() }
        }
        error("Google redirected this download too many times.")
    }

    suspend fun create(context: Context, sources: List<PdfSource>, title: String, progress: (PdfProgress) -> Unit,
        loader: suspend (PdfSource, File, Long) -> Unit = ::download): PdfCollectionResult = withContext(Dispatchers.IO) {
        require(sources.size <= MAX_FILES) { "Use up to $MAX_FILES distinct files per PDF." }
        PDFBoxResourceLoader.init(context.applicationContext)
        val root = File(context.cacheDir, "hymn-pdfs").apply { mkdirs() }
        val shared = File(root, "shared").apply { mkdirs() }
        // Only app-owned, expired exports are removed; saved/shared user copies are untouched.
        shared.listFiles()?.filter { it.isFile && it.lastModified() < System.currentTimeMillis() - 24 * 60 * 60 * 1000 }?.forEach { it.delete() }
        val temporary = File(root, UUID.randomUUID().toString()).apply { mkdirs() }
        var output: File? = null
        try {
            val failures = mutableListOf<PdfFailure>()
            var bytes = 0L; var pages = 0; var included = 0
            val merger = PDFMergerUtility()
            PDDocument(MemoryUsageSetting.setupTempFileOnly().setTempDir(temporary)).use { result ->
                sources.forEachIndexed { index, source ->
                    currentCoroutineContext().ensureActive()
                    withContext(Dispatchers.Main) { progress(PdfProgress(index, sources.size, source.link.title)) }
                    val input = File(temporary, "source-$index.pdf")
                    try {
                        loader(source, input, MAX_TOTAL_BYTES - bytes)
                        bytes += input.length()
                        require(input.length() <= MAX_FILE_BYTES && bytes <= MAX_TOTAL_BYTES) { "File exceeds the PDF size limit." }
                        PDDocument.load(input, MemoryUsageSetting.setupTempFileOnly().setTempDir(temporary)).use { document ->
                            require(!document.isEncrypted) { "Password-protected PDFs cannot be combined." }
                            require(document.numberOfPages > 0) { "This PDF has no pages." }
                            require(pages + document.numberOfPages <= MAX_PAGES) { "The combined PDF is limited to $MAX_PAGES pages." }
                            currentCoroutineContext().ensureActive()
                            val before = result.numberOfPages
                            try { merger.appendDocument(result, document) }
                            catch (e: Exception) { while(result.numberOfPages > before) result.removePage(result.numberOfPages - 1); throw e }
                            pages += document.numberOfPages; included++
                        }
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) {
                        val reason = if(e.message.orEmpty().contains("password", true)) "Password-protected PDFs cannot be combined."
                            else e.message?.take(220) ?: "This PDF could not be read."
                        failures += PdfFailure(source, reason)
                    } finally { input.delete() }
                }
                currentCoroutineContext().ensureActive()
                withContext(Dispatchers.Main) { progress(PdfProgress(sources.size, sources.size, "Finishing PDF")) }
                if (included > 0) {
                    output = File(shared, UUID.randomUUID().toString() + "-" + pdfFilename(title))
                    result.documentInformation.title = title
                    result.save(output)
                    currentCoroutineContext().ensureActive()
                }
            }
            PdfCollectionResult(output, included, pages, failures)
        } catch(e: Exception) { output?.delete(); throw e }
        finally { temporary.deleteRecursively() }
    }
}
