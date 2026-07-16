package fr.loevan.jeancalcul.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import dagger.hilt.android.AndroidEntryPoint
import fr.loevan.jeancalcul.ui.JeanCalculTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JeanCalculTheme {
                Surface {
                    Text("Jean-Calcul Assistant foundation")
                }
            }
        }
    }
}
