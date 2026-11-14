package io.github.mangi.eta.agent.terminal

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TerminalExecutionContextTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun rootChangesDefaultIdentityWithoutMovingTheWorkspace() {
        val context = TerminalExecutionContext(TerminalEnvironment.DEBIAN, null, "/eta/workspace")
        assertEquals("root", context.identityFor(TerminalEnvironment.ANDROID, true))
        assertEquals("user", context.identityFor(TerminalEnvironment.ANDROID, false))
        assertEquals("/eta/workspace", context.androidWorkspacePath)
        assertTrue(context.description(true).contains("identity=root"))
        assertTrue(context.description(false).contains("identity=user"))
    }

    @Test fun ordinaryAndroidAndProotShareThePrivateWorkspace() {
        val workspace = File(temporary.root, "terminal-user/workspace").path
        val context = TerminalExecutionContext(
            linuxEnvironment = TerminalEnvironment.DEBIAN,
            linuxRootfsPath = File(temporary.root, "terminal-user/proot/debian/rootfs").path,
            userWorkspacePath = workspace,
        )

        assertEquals(workspace, context.androidWorkspacePath)
        assertEquals(LinuxExecutionBackend.PROOT, context.linuxBackend)
        assertEquals("user", context.linuxIdentity)
        assertEquals("/workspace", context.linuxWorkspacePath)
        assertEquals(workspace, context.linuxHostWorkspacePath)
        assertFalse(context.linuxReady)
    }

    @Test fun chrootKeepsTheRootWorkspaceSeparateFromDefaultAndroid() {
        val workspace = File(temporary.root, "terminal-user/workspace").path
        val context = TerminalExecutionContext(
            linuxEnvironment = TerminalEnvironment.ALPINE,
            linuxRootfsPath = File(temporary.root, "terminal/alpine/rootfs").path,
            userWorkspacePath = workspace,
        )

        assertEquals(workspace, context.androidWorkspacePath)
        assertEquals(LinuxExecutionBackend.CHROOT, context.linuxBackend)
        assertEquals("root", context.linuxIdentity)
        assertEquals("/data/local/tmp/eta", context.linuxHostWorkspacePath)
    }

    @Test fun legacyProotKeepsTheExistingPrivateWorkspace() {
        val workspace = File(temporary.root, "terminal/workspace").path
        val context = TerminalExecutionContext(
            linuxEnvironment = TerminalEnvironment.DEBIAN,
            linuxRootfsPath = File(temporary.root, "terminal/proot/debian/rootfs").path,
            userWorkspacePath = workspace,
        )

        assertEquals(workspace, context.androidWorkspacePath)
        assertEquals("user", context.linuxIdentity)
        assertEquals(workspace, context.linuxHostWorkspacePath)
    }

    @Test fun descriptionReportsSelectedEnvironmentAndEscapesPaths() {
        val rootfs = File(temporary.root, "terminal-user/proot/debian/rootfs").apply { mkdirs() }
        File(rootfs, LinuxEnvironmentPaths.READY_MARKER).writeText("ready")
        val workspace = File(temporary.root, "work\n\"space").path
        val context = TerminalExecutionContext(TerminalEnvironment.DEBIAN, rootfs.path, workspace)

        val description = context.description(rootAvailable = false)
        assertTrue(context.linuxReady)
        assertFalse(context.linuxCommonToolsReady)
        assertTrue(context.linuxInstalledProfiles.isEmpty())
        assertTrue(description.contains("environment=android"))
        assertFalse(description.contains("environment=linux"))
        assertTrue(description.contains(JSONObject.quote(workspace)))
        assertFalse(description.contains(workspace))
        assertFalse(description.contains(rootfs.path))

        File(rootfs, AlpineEnvironmentPaths.COMMON_TOOLS_MARKER).writeText("toolset=${AlpineEnvironmentPaths.TOOLSET_REVISION}\n")
        File(rootfs, AlpineEnvironmentPaths.PYTHON_TOOLS_MARKER).writeText("profile=${AlpineEnvironmentPaths.PYTHON_TOOLS_REVISION}\n")
        File(rootfs, AlpineEnvironmentPaths.APK_ANALYSIS_MARKER).writeText("profile=${AlpineEnvironmentPaths.APK_ANALYSIS_REVISION}\n")
        assertTrue(context.linuxCommonToolsReady)
        assertEquals(listOf("python", "apk_analysis"), context.linuxInstalledProfiles)
        assertTrue(
            context.description(rootAvailable = false)
                .contains("environment=android"),
        )
    }

    @Test fun descriptionIncludesConfiguredSharedFolderMappings() {
        val context = TerminalExecutionContext(TerminalEnvironment.DEBIAN, null, "/eta/workspace", listOf(SharedFolderMount("Download", "/sdcard/Download")))
        val description = context.description(false)
        assertTrue(description.contains(JSONObject.quote("/eta/workspace")))
        assertFalse(description.contains("/workspace/mounts"))
        assertFalse(description.contains("environment=linux"))
    }
}
