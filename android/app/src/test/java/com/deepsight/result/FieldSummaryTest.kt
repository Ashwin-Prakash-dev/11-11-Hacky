package com.deepsight.result

import com.deepsight.engine.contract.DetectedObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FieldSummaryTest {
    @Test
    fun machineLabelsBecomeReadableWithoutPackSpecificMappings() {
        assertEquals("Early pre B like", displayClassLabel("early_pre_b_like"))
        assertEquals("Pre B like", displayClassLabel("pre_b_like"))
        assertEquals("Benign", displayClassLabel("benign"))
    }

    @Test
    fun wholeFieldPackNamesItsPredictionAndScore() {
        assertEquals("Model prediction: Malignant (94%)", wholeFieldPrediction(listOf(DetectedObject("malignant", 0.9443, null))))
        assertEquals("Model prediction: Benign (51%)", wholeFieldPrediction(listOf(DetectedObject("benign", 0.51, null))))
    }

    @Test
    fun cellPacksAndEmptyResultsHaveNoSinglePrediction() {
        assertNull(wholeFieldPrediction(emptyList()))
        assertNull(wholeFieldPrediction(listOf(DetectedObject("parasitized", 0.9, listOf(0.1, 0.1, 0.05, 0.05)))))
        assertNull(wholeFieldPrediction(listOf(DetectedObject("a", 0.9, null), DetectedObject("b", 0.8, null))))
    }

    @Test
    fun legendOnlyWhenBoxesAreDrawn() {
        assertTrue(hasBoxes(listOf(DetectedObject("parasitized", 0.9, listOf(0.1, 0.1, 0.05, 0.05)))))
        assertFalse(hasBoxes(listOf(DetectedObject("malignant", 0.9, null))))
        assertFalse(hasBoxes(emptyList()))
    }
}
