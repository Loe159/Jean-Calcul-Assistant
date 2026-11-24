package fr.loevan.jeancalcul.v2

import android.content.Context
import io.github.mangi.eta.agent.device.notifications.remote.MastraConnection
import io.github.mangi.eta.agent.device.notifications.remote.MastraPairing
import io.github.mangi.eta.food.*
import java.io.BufferedInputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.time.Instant
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList

internal class LocalFoodFixture(private val context: Context) : AutoCloseable {
    private val server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
    private val json = FoodContract.json
    private val now = Instant.now()
    private val rice = RecipeIngredient("ing_rice", 7, "Riz", Quantity(150.0, UnitCode.G), Scaling.LINEAR,
        null, null, false, emptyList())
    private val recipe = RecipeDefinition(
        "rcp_fixture", 1, null, "Riz aux légumes", "Recette de démonstration", 2, 5, 15, 7,
        Difficulty.EASY, listOf(MealType.DINNER), null, listOf(rice),
        listOf(
            step("st_heat", 1, "Faire chauffer l’eau", "Faites chauffer l’eau.", emptyList(), "Eau", 480),
            step("st_rice", 2, "Cuire le riz", "Versez {{ing:ing_rice}} de riz.", listOf(IngredientRef(rice.id, 1.0)), "Riz", 720),
        ), listOf(Equipment.STOVE), emptyList(), RecipeSource(RecipeSourceKind.MANUAL, null, null, null),
        null, null, AllergenCheck.NOT_GUARANTEED, now.toString(), now.toString(),
    )
    val completions = CopyOnWriteArrayList<CompleteRequest>()
    val feedback = CopyOnWriteArrayList<BriefFeedbackRequest>()

    init {
        MastraConnection.save(context, MastraPairing(
            "http://127.0.0.1:${server.localPort}", "jc_food_fixture",
            Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32) { 1 }),
        ))
    }

    private val thread = Thread {
        while (!server.isClosed) {
            val socket = try { server.accept() } catch (_: java.io.IOException) { break }
            socket.use { connection ->
                connection.soTimeout = 2_000
                runCatching {
                    val input = connection.getInputStream().buffered()
                    val path = input.readHttpLine().substringAfter(' ').substringBefore(' ')
                        .removePrefix("/mobile/v1/food")
                    var length = 0
                    while (true) {
                        val header = input.readHttpLine()
                        if (header.isEmpty()) break
                        if (header.startsWith("Content-Length:", ignoreCase = true)) {
                            length = header.substringAfter(':').trim().toInt()
                        }
                    }
                    val body = if (length == 0) "" else input.readNBytes(length).decodeToString()
                    val payload = response(path, body)?.encodeToByteArray()
                    val status = if (payload == null) "404 Not Found" else "200 OK"
                    val bytes = payload ?: """{"error":"NOT_FOUND","message":"Fixture"}""".encodeToByteArray()
                    val output = connection.getOutputStream()
                    output.write("HTTP/1.1 $status\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".encodeToByteArray())
                    output.write(bytes)
                    output.flush()
                }
            }
        }
    }.apply { isDaemon = true; start() }

    private fun response(path: String, body: String): String? = when {
        path == "/brief/cards" -> json.encodeToString(BriefCardsResponse(1, now.toString(), now.plusSeconds(600).toString(), listOf(
            BriefCard.RecipeRecommendation("rec_fixture", 40, recipe.title, "20 min", recipe.id, 20, 1.0, null, emptyList()),
        )))
        path == "/brief/feedback" -> {
            feedback += json.decodeFromString<BriefFeedbackRequest>(body)
            """{"schemaVersion":1,"replayed":false}"""
        }
        path == "/recipes/${recipe.id}" -> json.encodeToString(RecipeDetailResponse(
            1, recipe, RecipeLibrary(false, null, "", 0, null),
        ))
        path == "/recipes/${recipe.id}/availability" -> {
            val servings = json.decodeFromString<AvailabilityRequest>(body).servings
            val required = FoodQuantities.exact(rice.quantity, recipe.servings, servings, rice.scaling)
            json.encodeToString(Availability(
                1, recipe.id, recipe.version, servings, now.toString(), now.toString(),
                listOf(AvailabilityItem(rice.id, AvailabilityStatus.AVAILABLE, required, MeasuredQuantity(1000.0, UnitCode.G), null, null, null)),
                AvailabilitySummary(1.0, AvailabilityCategory.READY, 0),
            ))
        }
        path == "/pantry" -> json.encodeToString(PantryResponse(1, now.toString(), listOf(PantryLocation(1, "Réfrigérateur")), emptyList()))
        path.startsWith("/cooking-sessions/") && path.endsWith("/complete") -> {
            val request = json.decodeFromString<CompleteRequest>(body)
            val replayed = completions.any { it.operationId == request.operationId }
            if (!replayed) completions += request
            json.encodeToString(OperationResult(1, request.operationId, OperationState.SUCCEEDED, replayed, emptyList()))
        }
        path.startsWith("/cooking-sessions/") -> """{"schemaVersion":1,"session":$body}"""
        else -> null
    }

    override fun close() {
        MastraConnection.forget(context)
        server.close()
        thread.join(2_500)
    }

    private fun step(id: String, order: Int, title: String, instruction: String, refs: List<IngredientRef>, timer: String, seconds: Int) =
        RecipeStep(id, order, title, instruction, refs, seconds, null, TimerSuggestion(timer, seconds), emptyList(), null, emptyList())

    private fun BufferedInputStream.readHttpLine(): String = buildString {
        while (true) {
            val byte = read()
            if (byte == -1 || byte == 10) break
            if (byte != 13) append(byte.toChar())
        }
    }
}
