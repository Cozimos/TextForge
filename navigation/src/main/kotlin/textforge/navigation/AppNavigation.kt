
package textforge.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import textforge.ui.library.LibraryScreen
import textforge.ui.reader.ReaderScreen

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.Library.route
    ) {
        composable(Screen.Library.route) {
            LibraryScreen(
                onOpenNovel = { novelId ->
                    navController.navigate(
                        Screen.Reader.createRoute(novelId)
                    )
                }
            )
        }

        composable(
            route = Screen.Reader.route,
            arguments = listOf(
                navArgument("novelId") {
                    type = NavType.StringType
                }
            )
        ) { entry ->
            val novelId = entry.arguments?.getString("novelId") ?: return@composable

            ReaderScreen(
                novelId = novelId,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
