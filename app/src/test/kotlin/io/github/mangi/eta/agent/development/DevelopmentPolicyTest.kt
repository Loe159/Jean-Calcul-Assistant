package io.github.mangi.eta.agent.development

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DevelopmentPolicyTest {
    @Test fun exactEditsRejectMissingOrAmbiguousSource() {
        fun edit(before: String, after: String) = JSONArray().put(JSONObject().put("before", before).put("after", after))
        assertEquals("Hello Tools", DevelopmentPolicy.applyEdits("Hello tools", edit("tools", "Tools")))
        assertThrows(IllegalArgumentException::class.java) { DevelopmentPolicy.applyEdits("tools tools", edit("tools", "Tools")) }
        assertThrows(IllegalArgumentException::class.java) { DevelopmentPolicy.applyEdits("other", edit("tools", "Tools")) }
    }
    @Test fun cannotEditWorkflowsSigningOrEscapeRepositoryPaths() {
        for (path in listOf(".github/workflows/build.yml", "../app/src/Foo.kt", "app/src/../secret", "/app/src/File.kt", "app/src/keys.jks", "app/src/main/jniLibs/lib.so", "app/src//Foo.kt")) {
            assertThrows(path, IllegalArgumentException::class.java) { DevelopmentPolicy.requirePath(path) }
        }
        assertEquals("app/src/main/Foo.kt", DevelopmentPolicy.requirePath("app/src/main/Foo.kt"))
    }
    @Test fun approvalRequiresNewestPushForExactRevision() {
        fun run(id: Long, revision: String, status: String, conclusion: String) = JSONObject()
            .put("id", id).put("head_sha", revision).put("status", status).put("conclusion", conclusion).put("event", "push")
        val head = "a".repeat(40)
        val passed = run(1, head, "completed", "success")
        assertTrue(DevelopmentPolicy.successfulRuns(JSONArray().put(passed), head))
        assertFalse(DevelopmentPolicy.successfulRuns(JSONArray().put(passed).put(run(2, head, "in_progress", "null")), head))
        assertFalse(DevelopmentPolicy.successfulRuns(JSONArray().put(passed), "b".repeat(40)))
        assertFalse(DevelopmentPolicy.successfulRuns(JSONArray(), head))
    }
    @Test fun mergeChecksCannotBeEmptyPendingOrFailed() {
        fun check(id: Int, name: String, conclusion: String) = JSONObject().put("id", id).put("name", name)
            .put("status", "completed").put("conclusion", conclusion)
        val checks = JSONArray().put(check(1, "Build APK, unit tests and lint", "success"))
            .put(check(2, "Technical Android 35 E2E (no visual judgment)", "success"))
        assertTrue(DevelopmentPolicy.successfulChecks(checks))
        assertFalse(DevelopmentPolicy.successfulChecks(JSONArray()))
        assertFalse(DevelopmentPolicy.successfulChecks(checks.put(check(3, "Build APK, unit tests and lint", "failure"))))
    }
}
