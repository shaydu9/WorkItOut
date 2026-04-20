package com.cycling.workitout.data.network.core

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

/**
 * Single [Json] instance used by every Retrofit converter in the app.
 *
 * Config choices:
 *  - `ignoreUnknownKeys = true` — servers regularly add fields; we don't want
 *    to fail decoding just because a new `usage.cache_hit_tokens` appeared.
 *  - `isLenient = true` — tolerates quirky encodings (unquoted keys etc.) that
 *    show up occasionally in third-party payloads.
 *  - `explicitNulls = false` — `null` fields in the DTO are omitted from the
 *    outgoing JSON, keeping requests small and matching OkHttp FormBody
 *    behavior that we're replacing.
 */
@OptIn(ExperimentalSerializationApi::class)
internal val NetworkJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
}
