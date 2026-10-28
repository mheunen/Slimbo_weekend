package nl.spellenweekend.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import nl.spellenweekend.app.ui.AppRoot
import nl.spellenweekend.app.ui.SpellenTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SpellenTheme {
                AppRoot()
            }
        }
    }
}
