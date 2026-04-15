package com.wccslic.finanzainteligente

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MovementAdapter(
    private val movements: List<Movement>,
    private val onItemClick: (Movement) -> Unit
) : RecyclerView.Adapter<MovementAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvCategory: TextView = view.findViewById(R.id.tvCategory)
        val tvDescription: TextView = view.findViewById(R.id.tvDescription)
        val tvDate: TextView = view.findViewById(R.id.tvDate)
        val tvAmount: TextView = view.findViewById(R.id.tvAmount)
        val layoutBackground: View = view.findViewById(R.id.layoutBackground)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_movement, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val movement = movements[position]
        holder.tvCategory.text = movement.categoria
        holder.tvDescription.text = movement.descripcion
        
        val sdf = SimpleDateFormat("dd MMM yyyy", Locale("es", "MX"))
        holder.tvDate.text = sdf.format(Date(movement.fecha))

        // Formato de moneda MXN
        val mxnFormat = NumberFormat.getCurrencyInstance(Locale("es", "MX"))
        val amountFormatted = mxnFormat.format(movement.monto)

        if (movement.tipo == "Ingreso") {
            holder.tvAmount.text = "+ $amountFormatted"
            holder.tvAmount.setTextColor(Color.parseColor("#2E7D32"))
            holder.layoutBackground.setBackgroundColor(Color.parseColor("#F1F8E9")) // Verde muy claro
        } else {
            holder.tvAmount.text = "- $amountFormatted"
            holder.tvAmount.setTextColor(Color.parseColor("#C62828"))
            holder.layoutBackground.setBackgroundColor(Color.parseColor("#FFEBEE")) // Rojo muy claro
        }

        holder.itemView.setOnClickListener {
            onItemClick(movement)
        }
    }

    override fun getItemCount() = movements.size
}
