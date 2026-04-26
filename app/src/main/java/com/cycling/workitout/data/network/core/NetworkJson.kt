package com.cycling.workitout.data.network.core

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

// Shared JSON config: tolerant parsing, nulls omitted from outgoing requests.
@OptIn(ExperimentalSerializationApi::class)
internal val NetworkJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
}
