package io.github.mangi.eta.ui.navigation

import io.github.suqi8.coui.kmp.nav.core.NavBackStack
import io.github.suqi8.coui.kmp.nav.core.NavKey

class AgentNavigator(
    val backStack: NavBackStack,
) {
    fun push(route: AppRoute) {
        if (!route.isAvailableInJeanCalcul()) return
        if (route !in backStack) {
            backStack.add(route)
        }
    }

    fun replace(route: AppRoute) {
        if (!route.isAvailableInJeanCalcul()) return
        val existingIndex = backStack.indexOf(route)
        if (existingIndex >= 0) {
            while (backStack.lastIndex > existingIndex) {
                backStack.removeLastOrNull()
            }
            return
        }

        if (backStack.isNotEmpty()) {
            backStack[backStack.lastIndex] = route
        } else {
            backStack.add(route)
        }
    }

    fun pop(): Boolean {
        if (backStack.size <= 1) return false
        backStack.removeLastOrNull()
        return true
    }

    fun popToHome() {
        while (backStack.size > 1) {
            backStack.removeLastOrNull()
        }
        backStack.firstOrNull()?.let { first ->
            if (first !is AppRoute.Home) {
                backStack.clear()
                backStack.add(AppRoute.Home)
            }
        }
    }

    fun current(): NavKey? = backStack.lastOrNull()
}
