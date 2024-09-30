package com.example.model.weather

import kotlinx.serialization.Serializable

@Serializable
data class Refer(
    val sources: List<String>,
    val license: List<String>
)
