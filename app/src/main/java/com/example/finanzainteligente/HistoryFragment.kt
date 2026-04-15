package com.wccslic.finanzainteligente

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.util.Calendar

class HistoryFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private val db = FirebaseFirestore.getInstance()
    private lateinit var rvHistory: RecyclerView
    private var movementAdapter: MovementAdapter? = null
    private val movementList = mutableListOf<Movement>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_history, container, false)

        auth = FirebaseAuth.getInstance()
        rvHistory = view.findViewById(R.id.rvHistory)
        rvHistory.layoutManager = LinearLayoutManager(context)
        
        movementAdapter = MovementAdapter(movementList) { movement ->
            // Optional: handle item click
        }
        rvHistory.adapter = movementAdapter

        loadFullHistory()

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnDeuda = view.findViewById<Button>(R.id.btnDeuda)

        btnDeuda.setOnClickListener {
            startActivity(Intent(requireContext(), DeudaActivity::class.java))
        }
    }

    private fun loadFullHistory() {
        val uid = auth.currentUser?.uid ?: return
        val userRef = db.collection("usuarios_finanzas").document(uid)

        // 1. Listen for standard movements
        userRef.collection("movimientos")
            .orderBy("fecha", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Toast.makeText(context, "Error al cargar historial", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                val standardMovements = mutableListOf<Movement>()
                if (snapshots != null) {
                    for (doc in snapshots) {
                        val movement = doc.toObject(Movement::class.java)
                        movement.id = doc.id
                        standardMovements.add(movement)
                    }
                }

                // 2. Fetch recurring obligations to inject them into the list
                userRef.get().addOnSuccessListener { userDoc ->
                    val totalServicios = userDoc.getDouble("servicios") ?: 0.0
                    
                    userRef.collection("deudas").get().addOnSuccessListener { debtSnapshots ->
                        val finalHistory = mutableListOf<Movement>()
                        finalHistory.addAll(standardMovements)

                        // Create virtual movements for recurring obligations of the current month
                        val calendar = Calendar.getInstance()
                        calendar.set(Calendar.DAY_OF_MONTH, 1)
                        val startOfMonth = calendar.timeInMillis

                        // Add Debt Payments
                        for (debtDoc in debtSnapshots) {
                            val monto = debtDoc.getDouble("pagoMensual") ?: 0.0
                            val nombre = debtDoc.getString("nombre") ?: "Deuda"
                            if (monto > 0) {
                                finalHistory.add(Movement(
                                    id = "recurring_debt_${debtDoc.id}",
                                    monto = monto,
                                    tipo = "Gasto",
                                    categoria = "Deuda",
                                    descripcion = "Pago mensual: $nombre",
                                    fecha = startOfMonth,
                                    isDeuda = true
                                ))
                            }
                        }

                        // Add Recurring Services
                        if (totalServicios > 0) {
                            finalHistory.add(Movement(
                                id = "recurring_services",
                                monto = totalServicios,
                                tipo = "Gasto",
                                categoria = "Servicios",
                                descripcion = "Servicios recurrentes mensuales",
                                fecha = startOfMonth,
                                isDeuda = false
                            ))
                        }

                        // Sort the combined list by date
                        movementList.clear()
                        movementList.addAll(finalHistory.sortedByDescending { it.fecha })
                        movementAdapter?.notifyDataSetChanged()
                    }
                }
            }
    }
}
