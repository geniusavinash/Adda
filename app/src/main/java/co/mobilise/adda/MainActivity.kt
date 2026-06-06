package co.mobilise.adda

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import co.mobilise.adda.nav.AddaNavHost
import co.mobilise.adda.ui.theme.AddaBackground
import co.mobilise.adda.ui.theme.AddaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AddaTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = AddaBackground,
                ) {
                    AddaNavHost()
                }
            }
        }
    }
}
