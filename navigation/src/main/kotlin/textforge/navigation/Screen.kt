package textforge.navigation

sealed class Screen(val route: String) {
    data object Library : Screen("library")
    data object Reader : Screen("reader/{novelId}") {
        fun createRoute(novelId: String) = "reader/$novelId"
    }
}
