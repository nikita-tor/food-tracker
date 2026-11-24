package com.example.greetingcard.data

import org.json.JSONException
import org.json.JSONObject
import java.time.LocalDate

/**
 * Backup files the user saves with Android's file picker (to Google Drive, Downloads, …) and restores after a
 * reinstall or on a new phone. A backup wraps the same JSON as the app's own data file, plus when it was made.
 */
object Backup {
    const val MIME_TYPE = "application/json"

    /** What the file picker may offer when restoring. Some apps label .json files as plain text or binary. */
    val OPEN_TYPES = arrayOf(MIME_TYPE, "application/octet-stream", "text/plain")

    private const val APP = "FoodTracker"
    private const val FORMAT = 1

    fun fileName(day: LocalDate): String = "food-tracker-backup-$day.json"

    fun encode(data: AppData, createdAt: String, appVersion: String): String = JSONObject()
        .put("app", APP)
        .put("backupFormat", FORMAT)
        .put("createdAt", createdAt)
        .put("appVersion", appVersion)
        .put("data", JSONObject(AppDataJson.encode(data)))
        .toString(2)

    sealed interface Result {
        /** [createdAt] is null for a raw data file copied off a phone, which has no date in it. */
        data class Ok(val data: AppData, val createdAt: String?) : Result

        data class Invalid(val reason: String) : Result
    }

    /**
     * Reads a backup, or a raw `food_tracker.json`. Anything else is refused rather than loaded as empty data,
     * since restoring replaces everything on the phone.
     */
    fun decode(text: String): Result {
        val json = try {
            JSONObject(text)
        } catch (_: JSONException) {
            return Result.Invalid("This file isn't a Food Tracker backup.")
        }
        val dataJson = when {
            json.optString("app") == APP -> {
                if (json.optInt("backupFormat") > FORMAT) {
                    return Result.Invalid("This backup is from a newer version of the app. Update the app first.")
                }
                json.optJSONObject("data") ?: return Result.Invalid("This backup is empty.")
            }

            json.has("version") && (json.has("entries") || json.has("settings")) -> json

            else -> return Result.Invalid("This file isn't a Food Tracker backup.")
        }
        return try {
            Result.Ok(AppDataJson.decode(dataJson.toString()), json.optString("createdAt").ifEmpty { null })
        } catch (_: JSONException) {
            Result.Invalid("This backup is damaged and can't be read.")
        }
    }
}
