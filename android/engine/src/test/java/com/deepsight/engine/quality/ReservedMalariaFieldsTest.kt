package com.deepsight.engine.quality

import com.deepsight.engine.contract.Contracts
import com.deepsight.engine.contract.QualityReason
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNotNull
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

/** Optional local data evidence for #6; images are fetched, never committed or downloaded by CI. */
class ReservedMalariaFieldsTest {
    @Test
    fun `prepared originals pass and failure demos have intended rejection reasons`() {
        val directory = System.getenv("DEEPSIGHT_FIELDS")
        assumeNotNull(directory)
        val fieldsDir = requireNotNull(directory)
        val root = File(requireNotNull(System.getProperty("contractsDir"))).parentFile
        val selection = Contracts.json.parseToJsonElement(
            File(root, "ml/fixtures/malaria_fields.json").readText(),
        ).jsonObject
        val spec = Contracts.parseManifest(File(root, "ml/packs/malaria_thin/manifest.json").readText()).quality

        for (entry in selection.getValue("fields").jsonArray) {
            val field = entry.jsonObject
            val id = field.getValue("id").jsonPrimitive.content
            val filename = field.getValue("image").jsonObject.getValue("filename").jsonPrimitive.content
            val result = QualityGate.evaluate(readImage(File(fieldsDir, "$id/$filename")), spec)
            println("DeepSightFields $id: blur=${result.blurScore} exposure=${result.exposureScore} reasons=${result.reasons}")
            assertTrue("$id rejected: ${result.reasons}", result.pass)
        }
        for (entry in selection.getValue("variants").jsonArray) {
            val variant = entry.jsonObject
            val id = variant.getValue("id").jsonPrimitive.content
            val expected = when (variant.getValue("expected_reason").jsonPrimitive.content) {
                "blur" -> QualityReason.BLUR
                "overexposed" -> QualityReason.OVEREXPOSED
                else -> error("unknown expected quality reason")
            }
            val result = QualityGate.evaluate(readImage(File(fieldsDir, "$id/field.png")), spec)
            println("DeepSightFields $id: blur=${result.blurScore} exposure=${result.exposureScore} reasons=${result.reasons}")
            assertTrue("$id missing $expected: ${result.reasons}", !result.pass && expected in result.reasons)
        }
    }

    private fun readImage(file: File): PixelImage {
        require(file.isFile) { "Run ml/tools/prepare_malaria_fields.py first: missing $file" }
        val image = requireNotNull(ImageIO.read(file)) { "Cannot decode $file" }
        return PixelImage(image.width, image.height, image.getRGB(0, 0, image.width, image.height, null, 0, image.width))
    }
}
