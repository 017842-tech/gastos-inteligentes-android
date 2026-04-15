package com.wccslic.finanzainteligente

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.NumberFormat
import java.util.Locale

class DeudaAdapter(
    private val deudas: List<Debt>,
    private val onDeleteClick: (Debt) -> Unit
) : RecyclerView.Adapter<DeudaAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvDebtName: TextView = view.findViewById(R.id.tvDebtName)
        val tvDebtDetails: TextView = view.findViewById(R.id.tvDebtDetails)
        val btnDeleteDebt: ImageButton = view.findViewById(R.id.btnDeleteDebt)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_deuda, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val deuda = deudas[position]
        holder.tvDebtName.text = deuda.nombre
        
        val format = NumberFormat.getCurrencyInstance(Locale("es", "MX"))
        val mensualidad = format.format(deuda.pagoMensual)
        holder.tvDebtDetails.text = "${deuda.tipo} - Mensualidad: $mensualidad"

        holder.btnDeleteDebt.setOnClickListener {
            onDeleteClick(deuda)
        }
    }

    override fun getItemCount() = deudas.size
}
