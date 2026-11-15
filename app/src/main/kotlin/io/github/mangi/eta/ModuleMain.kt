package io.github.mangi.eta

import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import io.github.mangi.eta.config.Prefs
import io.github.mangi.eta.core.ModuleLogger
import io.github.mangi.eta.hook.system.SystemServerHooks

/** Optional framework bridge for virtual display and accessibility only. */
class ModuleMain : XposedModule() {
    override fun onModuleLoaded(param: ModuleLoadedParam) {
        if (!param.isSystemServer) { detach(); return }
        Prefs.attachRemote(runCatching { getRemotePreferences(Prefs.GROUP) }.getOrNull())
    }
    override fun onSystemServerStarting(param: SystemServerStartingParam) {
        SystemServerHooks.install(this, ModuleLogger(this), param.classLoader)
    }
}
