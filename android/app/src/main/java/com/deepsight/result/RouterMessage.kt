package com.deepsight.result

import com.deepsight.engine.contract.RouterResult
import com.deepsight.engine.contract.RouterVerdict

internal fun routerMessage(result: RouterResult): String = when (result.verdict) {
    RouterVerdict.MATCH -> "Image matches the selected test"
    RouterVerdict.MISMATCH -> "Image looks like another test: ${result.predicted ?: "unknown"}"
    RouterVerdict.REJECT -> "Image is not a recognised test type"
}
