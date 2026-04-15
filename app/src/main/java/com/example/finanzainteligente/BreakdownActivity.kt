package com.wccslic.finanzainteligente

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.NumberFormat
import java.util.Locale

class BreakdownActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private val db = FirebaseFirestore.getInstance()
    private val movementList = mutableListOf<Movement>()
    private lateinit var adapter: MovementAdapter
    private var filterType: String? = null // "Ingreso" o "Gasto"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_breakdown)

        auth = FirebaseAuth.getInstance()
        filterType = intent.getStringExtra("FILTER_TYPE")

        val tvTitle = findViewById<TextView>(R.id.tvBreakdownTitle)
        val rvBreakdown = findViewById<RecyclerView>(R.id.rvBreakdown)
        val tvMainTotal = findViewById<TextView>(R.id.tvTotalMainBreakdown)
        val tvSecondaryTotal = findViewById<TextView>(R.id.tvTotalSecondaryBreakdown)
        val tvFixedCommitments = findViewById<TextView>(R.id.tvFixedCommitments)

        tvTitle.text = if (filterType == "Ingreso") "Desglose de Ingresos" else "Desglose de Gastos"

        rvBreakdown.layoutManager = LinearLayoutManager(this)
        adapter = MovementAdapter(movementList) { }
        rvBreakdown.adapter = adapter

        loadBreakdownData(tvMainTotal, tvSecondaryTotal, tvFixedCommitments)
    }

    private fun loadBreakdownData(tvMain: TextView, tvSecondary: TextView, tvFixed: TextView) {
        val uid = auth.currentUser?.uid ?: return
        val userRef = db.collection("usuarios_finanzas").document(uid)
        val format = NumberFormat.getCurrencyInstance(Locale("es", "MX"))
        val now = System.currentTimeMillis()

        userRef.get().addOnSuccessListener { doc ->
            if (doc.exists()) {
                val servicios = doc.getDouble("servicios") ?: 0.0
                
                userRef.collection("deudas").get().addOnSuccessListener { debtSnaps ->
                    val totalMensualDeudas = debtSnaps.documents.sumOf { it.getDouble("pagoMensual") ?: 0.0 }
                    val totalFixed = servicios + totalMensualDeudas
                    
                    val pseudoMovements = mutableListOf<Movement>()
                    
                    // Solo procesar compromisos fijos si estamos viendo Gastos
                    if (filterType == "Gasto") {
                        tvFixed.visibility = View.VISIBLE
                        tvFixed.text = "Compromisos Mensuales (Servicios + Deudas): ${format.format(totalFixed)}"
                        
                        // Agregar Pseudo-movimiento para Servicios
                        if (servicios > 0) {
                            pseudoMovements.add(Movement(
                                monto = servicios,
                                tipo = "Gasto",
                                categoria = "Servicios",
                                descripcion = "Pago Recurrente de Servicios",
                                fecha = now
                            ))
                        }
                        
                        // Agregar Pseudo-movimientos para Deudas
                        for (dDoc in debtSnaps) {
                            val nombre = dDoc.getString("nombre") ?: "Deuda"
                            val pago = dDoc.getDouble("pagoMensual") ?: 0.0
                            val tasa = dDoc.getDouble("tasaInteres") ?: 0.0
                            if (pago > 0) {
                                pseudoMovements.add(Movement(
                                    monto = pago,
                                    tipo = "Gasto",
                                    categoria = "Deuda (Compromiso)",
                                    descripcion = "Pago Mensual: $nombre (Tasa: $tasa%)",
                                    fecha = now
                                ))
                            }
                        }
                    } else {
                        tvFixed.visibility = View.GONE
                    }
                    
                    userRef.collection("movimientos")
                        .orderBy("fecha", com.google.firebase.firestore.Query.Direction.DESCENDING)
                        .get().addOnSuccessListener { snapshots ->
                            movementList.clear()
                            val all = snapshots.toObjects(Movement::class.java)
                            
                            // Filtrar movimientos por tipo
                            val filteredMovements = all.filter { it.tipo == filterType }
                            
                            // Combinar con los compromisos mensuales si aplica
                            if (filterType == "Gasto") {
                                // Ponemos los compromisos arriba para visibilidad
                                movementList.addAll(pseudoMovements)
                            }
                            movementList.addAll(filteredMovements)
                            
                            adapter.notifyDataSetChanged()

                            if (filterType == "Ingreso") {
                                val totalIng = all.filter { it.tipo == "Ingreso" }.sumOf { it.monto }
                                val totalIngVar = all.filter { 
                                    it.tipo == "Ingreso" && 
                                    !it.isSaldoInicial && 
                                    it.categoria != "Ingresos Mensuales" &&
                                    !it.isDeuda 
                                }.sumOf { it.monto }

                                tvMain.text = "Ingresos Totales: ${format.format(totalIng)}"
                                tvMain.setTextColor(android.graphics.Color.parseColor("#2E7D32"))
                                
                                tvSecondary.visibility = View.VISIBLE
                                tvSecondary.text = "Ingresos Variables: ${format.format(totalIngVar)}"
                                tvSecondary.setTextColor(android.graphics.Color.parseColor("#2E7D32"))
                            } else {
                                val totalGasVar = all.filter { it.tipo == "Gasto" && !it.isDeuda }.sumOf { it.monto }
                                
                                tvMain.text = "Gastos Variables: ${format.format(totalGasVar)}"
                                tvMain.setTextColor(android.graphics.Color.parseColor("#C62828"))
                                
                                tvSecondary.visibility = View.VISIBLE
                                tvSecondary.text = "Gastos Totales (Var + Compromisos): ${format.format(totalGasVar + totalFixed)}"
                                tvSecondary.setTextColor(android.graphics.Color.parseColor("#C62828"))
                            }
                        }
                }
            }
        }
    }
}
