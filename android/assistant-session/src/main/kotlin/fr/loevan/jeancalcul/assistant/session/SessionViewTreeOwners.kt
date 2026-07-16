package fr.loevan.jeancalcul.assistant.session

import android.view.View
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/** Installs the session contracts on a window root or Compose view. */
internal fun View.installSessionViewTreeOwners(owner: SessionLifecycleOwner) {
    setViewTreeLifecycleOwner(owner)
    setViewTreeViewModelStoreOwner(owner)
    setViewTreeSavedStateRegistryOwner(owner)
}
