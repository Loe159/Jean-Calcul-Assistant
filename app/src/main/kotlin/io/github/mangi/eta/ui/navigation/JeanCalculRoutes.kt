package io.github.mangi.eta.ui.navigation

internal fun AppRoute.isAvailableInJeanCalcul(): Boolean = when (this) {
    AppRoute.Characters, is AppRoute.CharacterDetail, is AppRoute.CharacterEditor,
    AppRoute.CharacterPersona, is AppRoute.CharacterMemory, AppRoute.DataBackup,
    AppRoute.LinuxEnvironment, is AppRoute.LinuxFiles,
    AppRoute.CommunityCatalog, is AppRoute.CommunityCatalogProvider -> false
    is AppRoute.ModelProviderNew -> providerType == NewProviderType.OpenAiCompatible
    else -> true
}
