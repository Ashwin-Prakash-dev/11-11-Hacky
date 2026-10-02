package com.deepsight.engine.pack

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.deepsight.engine.onnx.OnnxModel
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PackLoaderDeviceTest {
    @Test
    fun loadsAssetPackAndCreatesOnnxModel() {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val loader = PackLoader.fromAssets(assets, packsRoot = "")

        val catalog = loader.discover()
        assertEquals(listOf("smoke"), catalog.installed.map { it.id })

        val pack = loader.load("smoke")
        OnnxModel(pack.modelBytes, OnnxModel.Accelerator.CPU).use { model ->
            assertEquals(OnnxModel.Accelerator.CPU, model.accelerator)
        }
    }
}
