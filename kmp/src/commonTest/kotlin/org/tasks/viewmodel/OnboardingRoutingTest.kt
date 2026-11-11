package org.tasks.viewmodel

import kotlin.test.Test
import kotlin.test.assertEquals

class OnboardingRoutingTest {

    private fun route(
        state: OnboardingState = OnboardingState(),
        hasAccount: Boolean? = null,
        isImporting: Boolean = false,
        isAddingAccount: Boolean = false,
    ) = routeOnboarding(
        state = state,
        hasAccount = hasAccount,
        isImporting = isImporting,
        isAddingAccount = isAddingAccount,
    )

    @Test
    fun routesToWelcomeWhenNoAccount() {
        assertEquals(
            OnboardingRouting(
                state = OnboardingState(wasInOnboarding = true),
                navigateTo = OnboardingScreen.WELCOME,
                ready = true,
            ),
            route(hasAccount = false),
        )
    }

    @Test
    fun doesNotReRouteToWelcomeOnceShown() {
        assertEquals(
            OnboardingRouting(
                state = OnboardingState(wasInOnboarding = true),
                ready = true,
            ),
            route(
                state = OnboardingState(wasInOnboarding = true),
                hasAccount = false,
            ),
        )
    }

    @Test
    fun waitsForImportToFinishBeforeLeavingOnboarding() {
        assertEquals(
            OnboardingRouting(OnboardingState(wasInOnboarding = true)),
            route(
                state = OnboardingState(wasInOnboarding = true),
                hasAccount = true,
                isImporting = true,
            ),
        )
    }

    @Test
    fun leavesOnboardingForHomeOnceAccountExists() {
        assertEquals(
            OnboardingRouting(
                state = OnboardingState(),
                navigateTo = OnboardingScreen.HOME,
                ready = true,
            ),
            route(
                state = OnboardingState(wasInOnboarding = true),
                hasAccount = true,
            ),
        )
    }

    @Test
    fun readyWithAccountAndNoPendingOnboarding() {
        assertEquals(
            OnboardingRouting(OnboardingState(), ready = true),
            route(hasAccount = true),
        )
    }

    @Test
    fun notReadyWhileAccountStateUnknown() {
        assertEquals(
            OnboardingRouting(OnboardingState(), ready = false),
            route(hasAccount = null),
        )
    }

    @Test
    fun losingTheLastAccountWhileAddingOneLeavesTheStackAlone() {
        assertEquals(
            OnboardingRouting(OnboardingState(), ready = true),
            route(hasAccount = false, isAddingAccount = true),
        )
    }

    @Test
    fun theDecisionIsRevisitedOnceTheyLeaveTheAddAccountFlow() {
        val deferred = route(hasAccount = false, isAddingAccount = true)

        assertEquals(
            OnboardingRouting(
                state = OnboardingState(wasInOnboarding = true),
                navigateTo = OnboardingScreen.WELCOME,
                ready = true,
            ),
            route(state = deferred.state, hasAccount = false),
        )
    }

    @Test
    fun alreadyBeingInOnboardingIsNotDisturbedByAddingAnAccount() {
        assertEquals(
            OnboardingRouting(OnboardingState(wasInOnboarding = true), ready = true),
            route(
                state = OnboardingState(wasInOnboarding = true),
                hasAccount = false,
                isAddingAccount = true,
            ),
        )
    }

    @Test
    fun addingASecondAccountNeverReachesTheExemption() {
        assertEquals(
            OnboardingRouting(OnboardingState(), ready = true),
            route(hasAccount = true, isAddingAccount = true),
        )
    }
}
