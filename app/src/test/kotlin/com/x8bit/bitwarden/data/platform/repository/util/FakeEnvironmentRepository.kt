package com.x8bit.bitwarden.data.platform.repository.util

import com.bitwarden.data.repository.model.Environment
import com.x8bit.bitwarden.data.platform.repository.EnvironmentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A faked implementation of [EnvironmentRepository] based on in-memory caching.
 */
class FakeEnvironmentRepository : EnvironmentRepository {

    override fun initialize() = Unit

    override var environment: Environment
        get() = mutableEnvironmentStateFlow.value
        set(value) {
            mutableEnvironmentStateFlow.value = value
            environmentSaved = true
        }

    /**
     * Tests start as if an environment was already chosen. Set this to false to simulate a
     * fresh install.
     */
    var environmentSaved: Boolean = true

    override val hasSavedEnvironment: Boolean
        get() = environmentSaved

    override val environmentStateFlow: StateFlow<Environment>
        get() = mutableEnvironmentStateFlow.asStateFlow()

    override fun saveCurrentEnvironmentForEmail(userEmail: String) = Unit

    override fun loadEnvironmentForEmail(userEmail: String): Boolean = true

    private val mutableEnvironmentStateFlow = MutableStateFlow<Environment>(Environment.Prod.Us)
}
