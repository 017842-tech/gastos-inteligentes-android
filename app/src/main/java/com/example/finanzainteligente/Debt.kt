package com.wccslic.finanzainteligente

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class Debt(
    var id: String = "",
    val nombre: String = "",
    val montoTotal: Double = 0.0,
    val pagoMensual: Double = 0.0,
    val tasaInteres: Double = 0.0,
    val tipo: String = "",
    val fecha: Long = 0L
)
