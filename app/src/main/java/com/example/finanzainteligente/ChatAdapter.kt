package com.wccslic.finanzainteligente

import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

class ChatAdapter(private val messages: List<ChatMessage>) :
    RecyclerView.Adapter<ChatAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cardMessage: MaterialCardView = view.findViewById(R.id.cardMessage)
        val tvSender: TextView = view.findViewById(R.id.tvSender)
        val tvMessage: TextView = view.findViewById(R.id.tvMessage)
        val layoutParent: LinearLayout = view as LinearLayout
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_message, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val chat = messages[position]
        holder.tvMessage.text = chat.message
        holder.tvSender.text = if (chat.sender == "User") "Tú" else "Asistente IA"

        val params = holder.cardMessage.layoutParams as LinearLayout.LayoutParams
        if (chat.sender == "User") {
            holder.layoutParent.gravity = Gravity.END
            holder.cardMessage.setCardBackgroundColor(Color.parseColor("#6200EE"))
            holder.tvMessage.setTextColor(Color.WHITE)
            holder.tvSender.setTextColor(Color.parseColor("#D1C4E9"))
        } else {
            holder.layoutParent.gravity = Gravity.START
            holder.cardMessage.setCardBackgroundColor(Color.WHITE)
            holder.tvMessage.setTextColor(Color.BLACK)
            holder.tvSender.setTextColor(Color.parseColor("#6200EE"))
        }
        holder.cardMessage.layoutParams = params
    }

    override fun getItemCount() = messages.size
}
