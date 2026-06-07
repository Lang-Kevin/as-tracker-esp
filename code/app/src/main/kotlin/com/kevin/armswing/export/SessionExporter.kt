package com.kevin.armswing.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.kevin.armswing.data.db.ArmSwingDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionExporter @Inject constructor(private val db: ArmSwingDatabase) {

    private val formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.systemDefault())

    suspend fun buildShareIntent(context: Context, sessionId: Long): Intent? =
        withContext(Dispatchers.IO) {
            val session = db.sessionDao().getById(sessionId) ?: return@withContext null
            val samples = db.velocitySampleDao().getSamplesOnce(sessionId)
            val omegas = samples.map { it.velocityMps }
            val durationS = session.endedAt?.let { (it - session.startedAt) / 1000 } ?: 0L

            val root = buildJsonObject {
                putJsonObject("session") {
                    put("id", session.id)
                    put("label", session.label)
                    put("started_at", ts(session.startedAt))
                    put("ended_at", session.endedAt?.let { JsonPrimitive(ts(it)) } ?: JsonNull)
                    put("duration_s", durationS)
                }
                putJsonObject("summary") {
                    put("max_omega", omegas.maxOrNull() ?: 0f)
                    put("avg_omega", if (omegas.isEmpty()) 0f else omegas.average().toFloat())
                    put("sample_count", samples.size)
                }
                putJsonArray("samples") {
                    samples.forEach { s ->
                        addJsonObject {
                            put("device_ts_ms", s.timestampMs)
                            put("elapsed_s", (s.timestampMs - session.startedAt).toDouble() / 1000.0)
                            put("omega", s.omega)
                        }
                    }
                }
            }

            val json = Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), root)
            val file = writeToCache(context, sessionId, json)
            shareIntent(context, file)
        }

    private fun ts(epochMs: Long): String = formatter.format(Instant.ofEpochMilli(epochMs))

    private fun writeToCache(context: Context, sessionId: Long, json: String): File {
        val dir = File(context.cacheDir, "exports").also { it.mkdirs() }
        return File(dir, "session_$sessionId.json").also { it.writeText(json) }
    }

    private fun shareIntent(context: Context, file: File): Intent {
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
