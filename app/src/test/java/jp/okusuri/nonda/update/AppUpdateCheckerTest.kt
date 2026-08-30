package jp.okusuri.nonda.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateCheckerTest {
    @Test
    fun newerPatchVersionIsDetected() {
        assertTrue(AppUpdateChecker.isNewer("1.0.1", "1.0"))
    }

    @Test
    fun versionPrefixIsAccepted() {
        assertTrue(AppUpdateChecker.isNewer("v2.0.0", "1.9.9"))
    }

    @Test
    fun sameVersionIsNotNewer() {
        assertFalse(AppUpdateChecker.isNewer("1.0.0", "1.0"))
    }

    @Test
    fun olderVersionIsNotNewer() {
        assertFalse(AppUpdateChecker.isNewer("0.9.9", "1.0"))
    }
}
