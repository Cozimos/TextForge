package textforge.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import textforge.storage.NovelStore
import textforge.ui.library.LibraryScreen
import textforge.ui.reader.ReaderScreen

@Composable
fun AppNavigation(
    incomingUri: Uri? = null,
    onIncomingUriConsumed: () -> Unit = {}
) {
    val context = LocalContext.current.applicationContext
    val navController = rememberNavController()

    val startDestinationState = remember {
        mutableStateOf<String?>(null)
    }

    LaunchedEffect(Unit) {
        startDestinationState.value = if (incomingUri != null) {
            Screen.Library.route
        } else {
            withContext(Dispatchers.IO) {
                NovelStore.getRestorableNovelId(context)
                    ?.let { Screen.Reader.createRoute(it) }
                    ?: Screen.Library.route
            }
        }
    }

    val startDestination = startDestinationState.value ?: return
    val currentEntry by navController.currentBackStackEntryAsState()

    LaunchedEffect(incomingUri, currentEntry?.destination?.route) {
        if (incomingUri != null &&
            currentEntry?.destination?.route != Screen.Library.route
        ) {
            navController.navigate(Screen.Library.route) {
                launchSingleTop = true
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Library.route) {
            LibraryScreen(
                incomingUri = incomingUri,
                onIncomingUriConsumed = onIncomingUriConsumed,
                onOpenNovel = { novelId ->
                    NovelStore.saveLastNovelId(context, novelId)

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
            val novelId = entry.arguments?.getString("novelId")
                ?: return@composable

            ReaderScreen(
                novelId = novelId,
                onBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate(Screen.Library.route) {
                            popUpTo(Screen.Reader.route) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                }
            )
        }
    }
}
