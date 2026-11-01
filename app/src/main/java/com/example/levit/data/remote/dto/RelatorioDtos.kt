package com.example.levit.data.remote.dto

/** { atual, anterior, variacao } de um indicador comparado ao período anterior. */
data class Comparacao(val atual: Double?, val anterior: Double?, val variacao: Double?)