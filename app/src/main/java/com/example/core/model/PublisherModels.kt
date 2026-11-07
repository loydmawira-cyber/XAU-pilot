package com.example.core.model

enum class EaSubmissionStatus {
    DRAFT,
    SUBMITTED,
    APPROVED,
    REJECTED
}

data class EaSubmission(
    val id: String,
    val publisherId: String,
    val publisherName: String,
    val name: String,
    val version: String,
    val fileName: String,
    val fileSizeBytes: Long,
    val strategyCategory: StrategyCategory,
    val description: String,
    val supportedSymbols: List<String> = listOf("XAUUSD"),
    val monthlyPriceUsd: Double = 49.0,
    val status: EaSubmissionStatus = EaSubmissionStatus.DRAFT,
    val submittedAtUtc: String? = null,
    val reviewNote: String? = null
)
