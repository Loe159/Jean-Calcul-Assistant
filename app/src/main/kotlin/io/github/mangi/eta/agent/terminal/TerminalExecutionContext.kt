package io.github.mangi.eta.agent.terminal

import java.io.File
import org.json.JSONObject

/** 同一次运行固定目录和 Linux 环境；Android 默认身份根据已确认的授权选择。 */
internal data class TerminalExecutionContext(
    val linuxEnvironment: TerminalEnvironment,
    val linuxRootfsPath: String?,
    val userWorkspacePath: String = TerminalRuntime.userWorkspacePath,
    val sharedMounts: List<SharedFolderMount> = emptyList(),
) {
    init {
        require(linuxEnvironment.isLinux)
    }

    val androidWorkspacePath: String
        get() = userWorkspacePath

    val linuxBackend: LinuxExecutionBackend
        get() = LinuxEnvironmentPaths.backendOf(linuxRootfsPath)

    val linuxIdentity: String
        get() = if (linuxBackend == LinuxExecutionBackend.PROOT) "user" else "root"

    val linuxWorkspacePath: String
        get() = TerminalRuntime.LINUX_WORKSPACE_PATH

    val linuxHostWorkspacePath: String
        get() = if (linuxIdentity == "root") TerminalRuntime.ROOT_WORKSPACE_PATH else userWorkspacePath

    val linuxReady: Boolean
        get() = LinuxEnvironmentPaths.rootfsReady(linuxRootfsPath)

    val linuxCommonToolsReady: Boolean
        get() = AlpineEnvironmentPaths.commonToolsReady(linuxRootfsPath)

    val linuxInstalledProfiles: List<String>
        get() {
            if (!linuxReady || linuxRootfsPath.isNullOrBlank()) return emptyList()
            val rootfs = File(linuxRootfsPath)
            return buildList {
                LinuxPackageProfiles.ALL.forEach { profile ->
                    if (linuxPackageProfileReady(rootfs, profile)) add(profile.id)
                }
                if (linuxApkAnalysisReady(rootfs)) add(APK_ANALYSIS_PROFILE_ID)
            }
        }

    fun identityFor(environment: TerminalEnvironment, rootAvailable: Boolean): String =
        if (environment.isLinux) linuxIdentity else if (rootAvailable) "root" else "user"

    fun description(rootAvailable: Boolean): String = buildString {
        append("Commandes et fichiers : environment=android, identity=")
        append(identityFor(TerminalEnvironment.ANDROID, rootAvailable))
        append(", workspace=")
        append(JSONObject.quote(androidWorkspacePath))
        append(". Use only the Android shell and available tools. Linux and development installers are not included in this product.")
    }

    private companion object {
        const val APK_ANALYSIS_PROFILE_ID = "apk_analysis"
    }
}
