package io.github.mangi.eta.devpreview

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DevHubJsonTest {
    private val builtAt = "2026-01-10T12:00:00Z"

    @Test fun readsCliBuildAsset() {
        val info = DevBuildInfo.parseAsset("""
            {"buildId":"build-example","branch":"feat/example","commit":"${"a".repeat(40)}",
            "dirty":true,"builtAt":"$builtAt","message":"A synthetic build","hubUrl":"https://hub.example:4120"}
        """.trimIndent())
        assertEquals(DevBuildInfo("build-example", "feat/example", "a".repeat(40), true, builtAt), info)
    }

    @Test fun missingOrMalformedAssetLeavesBuildUnknown() {
        assertNull(DevBuildInfo.parseAsset(null))
        assertNull(DevBuildInfo.parseAsset("invalid"))
        assertNull(DevBuildInfo.parseAsset("""{"buildId":"example-build"}"""))
    }

    @Test fun readsStateAndReviewDetails() {
        val state = DevHubJson.state(JSONObject("""
            {"seq":18,"branches":[{"branch":"feat/example","worktree":"Example","builds":[
              {"id":"build-example","branch":"feat/example","worktree":"Example","commit":"${"a".repeat(40)}",
               "dirty":false,"message":"Demo change","versionName":"2.0.0-dev",
               "sha256":"${"b".repeat(64)}","size":123,"createdAt":"$builtAt"}]}],
             "reviews":[
               {"id":"review-answered","branch":"feat/example","title":"Previous","status":"answered",
                "createdAt":"$builtAt","answer":{"verdict":"choice","choice":"A","comment":"Fits","answeredAt":"$builtAt",
                                                   "installedBuildId":"build-example"}},
               {"id":"review-open","branch":"feat/example","buildId":"build-example","title":"Compare rows",
                "body":"Which one fits?","status":"open","images":["sample.png"],"previewFqn":null,"createdAt":"$builtAt",
                "options":[{"id":"A","label":"Compact","image":"compact.png","previewFqn":"example.RowsKt.CompactPreview"},
                           {"id":"B","label":"Wide","image":null,"previewFqn":null}],"answer":null}]}
        """.trimIndent()))
        assertEquals(18L, state.seq)
        assertEquals(123L, state.findBuild("build-example")?.size)
        assertEquals(builtAt, state.findBuild("build-example")?.info?.createdAt)
        val open = state.reviews.first()
        assertEquals("review-open", open.id)
        assertEquals("build-example", open.buildId)
        assertNull(open.answer)
        assertNull(open.previewFqn)
        assertEquals(listOf("sample.png"), open.images)
        assertEquals("compact.png", open.options.first().image)
        assertEquals("example.RowsKt.CompactPreview", open.options.first().previewFqn)
        assertNull(open.options.last().image)
        assertEquals(DevReviewAnswer("choice", "A", "Fits", builtAt, "build-example"), state.reviews.last().answer)
    }

    @Test fun eventCursorCanRequestResync() {
        val batch = DevHubJson.events(JSONObject("""
            {"seq":19,"events":[{"seq":19,"type":"review","id":"review-example","branch":"feat/example"}]}
        """.trimIndent()))
        assertFalse(batch.resync)
        assertEquals(listOf(DevEvent(19, "review", "review-example", "feat/example")), batch.events)

        val resync = DevHubJson.events(JSONObject("""{"seq":3,"events":[],"resync":true}"""))
        assertTrue(resync.resync)
        assertEquals(3L, resync.seq)
        assertTrue(resync.events.isEmpty())
    }

    @Test fun producesChoiceOnlyForAnApprovalWithAnOption() {
        val approved = DevHubJson.answerPayload(true, null, "", null)
        assertEquals(setOf("verdict"), approved.keys().asSequence().toSet())
        assertEquals("approved", approved.getString("verdict"))

        val choice = DevHubJson.answerPayload(true, "B", " Use this layout. ", "build-example")
        assertEquals("choice", choice.getString("verdict"))
        assertEquals("B", choice.getString("choice"))
        assertEquals("Use this layout.", choice.getString("comment"))
        assertEquals("build-example", choice.getString("installedBuildId"))

        val changes = DevHubJson.answerPayload(false, "A", "Wider space", null)
        assertEquals("changes", changes.getString("verdict"))
        assertFalse(changes.has("choice"))
    }

    @Test fun readsCatalogEntries() {
        val catalog = DevPreviewEntry.parse(JSONArray("""
            [{"fqn":"example.RowsKt.CompactPreview","file":"example/Rows.kt","name":"CompactPreview"}]
        """.trimIndent()))
        assertEquals(listOf(DevPreviewEntry("example.RowsKt.CompactPreview", "example/Rows.kt", "CompactPreview")), catalog)
    }

    @Test fun catalogHidesHubScreensAndShowsReadableLabels() {
        val product = DevPreviewEntry("example.SettingsKt.SettingsPageSpecimenPreview", "example/Settings.kt", "SettingsPageSpecimenPreview")
        val hub = DevPreviewEntry("io.github.mangi.eta.devpreview.DevHubScreenKt.DevHubReviewsPreview", "x.kt", "DevHubReviewsPreview")
        assertEquals(listOf(product), DevPreviewEntry.catalog(listOf(product, hub)))
        assertEquals("Settings page specimen", product.label)
    }
}
