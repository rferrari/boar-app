package team.sopa.boar.engine.rag

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentImportLimitTest {
    @Test fun `files over 25 MB are refused before reading, unknown sizes are read`() {
        assertFalse(DocumentImport.tooLargeToRead(25_000_000L))
        assertTrue(DocumentImport.tooLargeToRead(25_000_001L))
        assertTrue(DocumentImport.tooLargeToRead(90_000_000L))
        assertFalse(DocumentImport.tooLargeToRead(0L))
        assertFalse(DocumentImport.tooLargeToRead(-1L))
    }
}
