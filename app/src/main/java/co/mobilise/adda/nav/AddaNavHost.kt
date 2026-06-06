package co.mobilise.adda.nav

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import co.mobilise.adda.state.AppViewModel
import co.mobilise.adda.ui.screens.ConnectedScreen
import co.mobilise.adda.ui.screens.HomeScreen
import co.mobilise.adda.ui.screens.HostScreen
import co.mobilise.adda.ui.screens.JoinScreen
import co.mobilise.adda.ui.screens.QaScreen
import co.mobilise.adda.ui.screens.SplashScreen

/**
 * Central nav graph. A single activity-scoped [AppViewModel] is obtained here
 * and threaded into every screen so HOST/CLIENT mode + subject are shared.
 */
@Composable
fun AddaNavHost(navController: NavHostController = rememberNavController()) {
    val app: AppViewModel = viewModel()

    NavHost(navController = navController, startDestination = Routes.SPLASH) {

        composable(Routes.SPLASH) {
            SplashScreen(
                onReady = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.HOME) {
            HomeScreen(
                onHost = {
                    app.startHost(subject = "General")
                    navController.navigate(Routes.HOST)
                },
                onJoin = {
                    app.startClient()
                    navController.navigate(Routes.JOIN)
                },
            )
        }

        composable(Routes.HOST) {
            HostScreen(
                app = app,
                onOpenQa = { navController.navigate(Routes.QA) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.JOIN) {
            JoinScreen(
                app = app,
                onConnected = { navController.navigate(Routes.CONNECTED) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.CONNECTED) {
            ConnectedScreen(
                app = app,
                onEnterQa = { navController.navigate(Routes.QA) },
            )
        }

        composable(Routes.QA) {
            QaScreen(
                app = app,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
