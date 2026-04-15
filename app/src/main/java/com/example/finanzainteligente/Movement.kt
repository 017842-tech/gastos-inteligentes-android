package com.wccslic.finanzainteligente

import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName

@IgnoreExtraProperties
data class Movement(
    var id: String = "",
    @get:PropertyName("monto") @set:PropertyName("monto") var monto: Double = 0.0,
    @get:PropertyName("tipo") @set:PropertyName("tipo") var tipo: String = "",
    @get:PropertyName("categoria") @set:PropertyName("categoria") var categoria: String = "",
    @get:PropertyName("descripcion") @set:PropertyName("descripcion") var descripcion: String = "",
    @get:PropertyName("fecha") @set:PropertyName("fecha") var fecha: Long = 0L,
    @get:PropertyName("isDeuda") @set:PropertyName("isDeuda") var isDeuda: Boolean = false,
    @get:PropertyName("isSaldoInicial") @set:PropertyName("isSaldoInicial") var isSaldoInicial: Boolean = false
)
