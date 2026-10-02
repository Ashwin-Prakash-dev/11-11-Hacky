package com.deepsight

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.deepsight.data.CaseDb
import com.deepsight.ui.theme.DeepSightTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { DeepSightTheme { DeepSightApp(CaseRunner.get(this), CaseDb.get(this).dao()) } }
    }
}
