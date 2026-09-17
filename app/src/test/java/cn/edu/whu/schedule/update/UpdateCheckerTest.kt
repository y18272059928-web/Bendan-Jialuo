package cn.edu.whu.schedule.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    @Test
    fun comparesSemanticVersionsNumerically() {
        assertTrue(UpdateChecker.compareVersions("0.5.0", "0.4.9") > 0)
        assertTrue(UpdateChecker.compareVersions("0.10.0", "0.9.9") > 0)
        assertTrue(UpdateChecker.compareVersions("1.0", "1.0.1") < 0)
        assertEquals(0, UpdateChecker.compareVersions("v1.2.0", "1.2"))
    }
}