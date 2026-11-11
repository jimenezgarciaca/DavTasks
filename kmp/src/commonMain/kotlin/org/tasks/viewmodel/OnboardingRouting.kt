package org.tasks.viewmodel

enum class OnboardingScreen {
    WELCOME,
    HOME,
}

data class OnboardingState(
    val wasInOnboarding: Boolean = false,
)

data class OnboardingRouting(
    val state: OnboardingState,
    /** Pantalla a la que navegar vaciando la pila, o null si no hay que moverse. */
    val navigateTo: OnboardingScreen? = null,
    val ready: Boolean? = null,
)

fun routeOnboarding(
    state: OnboardingState,
    hasAccount: Boolean?,
    isImporting: Boolean,
    isAddingAccount: Boolean = false,
): OnboardingRouting = when (hasAccount) {
    false -> when {
        isAddingAccount -> OnboardingRouting(state, ready = true)
        !state.wasInOnboarding -> OnboardingRouting(
            state = state.copy(wasInOnboarding = true),
            navigateTo = OnboardingScreen.WELCOME,
            ready = true,
        )
        else -> OnboardingRouting(state, ready = true)
    }
    true -> when {
        isImporting -> OnboardingRouting(state)
        state.wasInOnboarding -> OnboardingRouting(
            state = state.copy(wasInOnboarding = false),
            navigateTo = OnboardingScreen.HOME,
            ready = true,
        )
        else -> OnboardingRouting(state, ready = true)
    }
    null -> OnboardingRouting(state, ready = false)
}
