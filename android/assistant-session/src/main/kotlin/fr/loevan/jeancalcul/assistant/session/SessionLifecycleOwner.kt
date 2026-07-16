package fr.loevan.jeancalcul.assistant.session

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

/**
 * Supplies the view-tree contracts normally inherited from an activity or fragment to a session.
 */
internal class SessionLifecycleOwner :
    LifecycleOwner,
    SavedStateRegistryOwner,
    ViewModelStoreOwner {
    private val registry = LifecycleRegistry.createUnsafe(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    init {
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
    }

    override val lifecycle: Lifecycle
        get() = registry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override val viewModelStore = ViewModelStore()

    fun create() {
        if (registry.currentState == Lifecycle.State.INITIALIZED) {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        }
    }

    fun show() {
        create()
        when (registry.currentState) {
            Lifecycle.State.CREATED -> {
                registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
                registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            }

            Lifecycle.State.STARTED -> registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            Lifecycle.State.INITIALIZED,
            Lifecycle.State.RESUMED,
            Lifecycle.State.DESTROYED,
            -> Unit
        }
    }

    fun destroy() {
        if (registry.currentState != Lifecycle.State.DESTROYED) {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
        viewModelStore.clear()
    }
}
