package com.tinyblacksmith.core

import com.tinyblacksmith.core.persistence.SaveCodec
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import kotlin.test.Test

/**
 * `./gradlew :core:scenarios` (not part of the default suite): writes every [ScenarioSaves] case as `<id>.json`, the
 * save exactly as `SaveCodec.encodeRun` stores it, and `index.json` for the debug menu, into the directory named by
 * the `scenarios.out` system property (the app's debug assets). With `-Dscenarios.search=true` it prints candidate
 * seeds for re-pinning instead and writes nothing.
 */
class ScenarioSavesWriter {
    @Test
    fun write() {
        if (System.getProperty("scenarios.search") == "true") { println(ScenarioSaves.candidates()); return }
        val out = File(requireNotNull(System.getProperty("scenarios.out")) { "scenarios.out is not set; run ./gradlew :core:scenarios" }).apply { mkdirs() }
        out.listFiles { f -> f.extension == "json" }?.forEach { it.delete() }
        for (sc in ScenarioSaves.all) File(out, "${sc.id}.json").writeText(SaveCodec.encodeRun(sc.state))
        val index = JsonArray(ScenarioSaves.all.map { sc ->
            JsonObject(buildMap {
                put("id", JsonPrimitive(sc.id)); put("title", JsonPrimitive(sc.title)); put("description", JsonPrimitive(sc.description)); put("built", JsonPrimitive(sc.built))
                put("endDay", JsonPrimitive(sc.endDay)); put("ownLegacy", JsonPrimitive(sc.ownLegacy))
                sc.card?.let { put("card", JsonPrimitive(it.name)) }
            })
        })
        File(out, "index.json").writeText(index.toString())
        println("Wrote ${ScenarioSaves.all.size} scenario saves to $out")
    }
}
