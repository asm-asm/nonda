package jp.okusuri.nonda.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetDoseSelectorTest {
    @Test
    fun morningButtonAppearsAtStartOfNewDay() {
        assertEquals(
            "朝",
            WidgetDoseSelector.pendingType(
                current = "06:00",
                eveningTime = "20:00",
                morningTaken = false,
                eveningTaken = false,
            ),
        )
    }

    @Test
    fun eveningButtonAppearsAfterMorningWasTaken() {
        assertEquals(
            "夜",
            WidgetDoseSelector.pendingType(
                current = "09:00",
                eveningTime = "20:00",
                morningTaken = true,
                eveningTaken = false,
            ),
        )
    }

    @Test
    fun eveningTakesPriorityAfterEveningTime() {
        assertEquals(
            "夜",
            WidgetDoseSelector.pendingType(
                current = "20:01",
                eveningTime = "20:00",
                morningTaken = false,
                eveningTaken = false,
            ),
        )
    }

    @Test
    fun buttonIsHiddenOnlyAfterBothDosesWereTaken() {
        assertNull(
            WidgetDoseSelector.pendingType(
                current = "21:00",
                eveningTime = "20:00",
                morningTaken = true,
                eveningTaken = true,
            ),
        )
    }
}
