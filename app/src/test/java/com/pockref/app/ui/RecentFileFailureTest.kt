package com.pockref.app.ui

import java.io.FileNotFoundException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentFileFailureTest {

    @Test
    fun `recognizes a direct file-not-found exception`() {
        assertTrue(isMissingDocumentFailure(FileNotFoundException("gone")))
    }

    @Test
    fun `recognizes the Samsung DocumentsProvider missing-file wrapper`() {
        val error = IllegalArgumentException(
            "Failed to query: java.io.FileNotFoundException: Missing file for primary:Download/test.pur",
        )

        assertTrue(isMissingDocumentFailure(error))
    }

    @Test
    fun `recognizes a missing file as the cause of a provider wrapper`() {
        val error = IllegalArgumentException("Provider query failed", FileNotFoundException("gone"))

        assertTrue(isMissingDocumentFailure(error))
    }

    @Test
    fun `does not classify an unrelated invalid URI as a missing file`() {
        assertFalse(isMissingDocumentFailure(IllegalArgumentException("Not a document URI")))
    }
}
