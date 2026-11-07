package com.phantominnovation.choir

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class PdfCollectionTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun source(id: String) = pdfSource(DriveLink(id,"https://drive.google.com/file/d/${id}_file_123/view"),listOf(1))
    private fun makePdf(file: File, width: Float, pages: Int = 1) {
        PDFBoxResourceLoader.init(context)
        PDDocument().use { pdf -> repeat(pages) { pdf.addPage(PDPage(PDRectangle(width,300f))) };pdf.save(file) }
    }
    @Test fun preservesPageOrderReportsFailuresAndSharesThroughAReadOnlyContentUri() = runBlocking {
        val result=PdfCollection.create(context,listOf(source("first"),source("bad"),source("second")),"Sunday test",{},loader={s,file,_->
            if(s.link.title=="bad")file.writeText("not a pdf") else makePdf(file,if(s.link.title=="first")201f else 202f,if(s.link.title=="first")1 else 2)
        })
        try {
            assertEquals(2,result.included);assertEquals(3,result.pages);assertEquals(1,result.failures.size)
            PDDocument.load(result.file).use { pdf -> assertEquals(listOf(201f,202f,202f),pdf.pages.map { it.mediaBox.width }) }
            val uri=FileProvider.getUriForFile(context,"${context.packageName}.pdfs",result.file!!)
            assertEquals("content",uri.scheme)
            context.contentResolver.openInputStream(uri)!!.use { assertEquals("%PDF-",String(it.readNBytes(5))) }
            PdfRenderer(ParcelFileDescriptor.open(result.file,ParcelFileDescriptor.MODE_READ_ONLY)).use { assertEquals(3,it.pageCount) }
        } finally { result.file?.delete() }
    }
    @Test fun cancellationAndAllFailedCollectionsDoNotCreateShareableFiles() = runBlocking {
        val none=PdfCollection.create(context,listOf(source("bad")),"Failed",{},loader={_,_,_->error("Private file")})
        assertNull(none.file);assertEquals(1,none.failures.size)
        try {
            PdfCollection.create(context,listOf(source("cancel")),"Cancelled",{},loader={_,_,_->throw CancellationException("Cancelled")})
            fail("Cancellation must propagate")
        } catch (_: CancellationException) {}
        val root=File(context.cacheDir,"hymn-pdfs")
        assertTrue(root.listFiles().orEmpty().all { it.name=="shared" })
    }
    @Test fun realPublicPdfAndGoogleDocCombineWithoutGoogleCredentials() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("pdfNetwork")=="true")
        val sources=listOf(
            pdfSource(DriveLink("As we gather.pdf","https://drive.google.com/file/d/1rSSwIm1sEFiM4Z4fPWQSN8iCTiLmHEO5/view"),listOf(1)),
            pdfSource(DriveLink("Called To walk","https://docs.google.com/document/d/1U360hXcURI-ugHIU6jh-iTklLQ8yogm5YkRJAGzqxY4/edit"),listOf(2))
        )
        val result=PdfCollection.create(context,sources,"Live PDF verification",{})
        assertEquals(result.failures.toString(),2,result.included);assertTrue(result.failures.isEmpty());assertTrue(result.pages>=2)
        result.file!!.copyTo(File(context.filesDir,"live-combined.pdf"),overwrite=true)
        PdfRenderer(ParcelFileDescriptor.open(result.file,ParcelFileDescriptor.MODE_READ_ONLY)).use { pdf ->
            pdf.openPage(0).use { page ->
                val bitmap=Bitmap.createBitmap(page.width*2,page.height*2,Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                File(context.filesDir,"live-combined-page.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
                bitmap.recycle()
            }
        }
        result.file.delete()
        Unit
    }
}
