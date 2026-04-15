package com.wccslic.finanzainteligente

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue

class ManageDeudasActivity : AppCompatActivity() {

    private lateinit var rvDeudas: RecyclerView
    private lateinit var adapter: DeudaAdapter
    private val deudaList = mutableListOf<Debt>()
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_deuda_list)

        rvDeudas = findViewById(R.id.rvDeudas)
        rvDeudas.layoutManager = LinearLayoutManager(this)
        adapter = DeudaAdapter(deudaList) { debt ->
            showDeleteConfirmation(debt)
        }
        rvDeudas.adapter = adapter

        loadDeudas()
    }

    private fun loadDeudas() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("usuarios_finanzas").document(uid).collection("deudas")
            .addSnapshotListener { snapshots, e ->
                if (e != null) return@addSnapshotListener
                
                deudaList.clear()
                if (snapshots != null) {
                    for (doc in snapshots) {
                        val debt = doc.toObject(Debt::class.java).apply { id = doc.id }
                        deudaList.add(debt)
                    }
                }
                adapter.notifyDataSetChanged()
            }
    }

    private fun showDeleteConfirmation(debt: Debt) {
        AlertDialog.Builder(this)
            .setTitle("Eliminar Deuda")
            .setMessage("¿Deseas eliminar '${debt.nombre}'? Esto detendrá los cargos mensuales recurrentes.")
            .setPositiveButton("Eliminar") { _, _ ->
                deleteDebt(debt)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun deleteDebt(debt: Debt) {
        val uid = auth.currentUser?.uid ?: return
        val userRef = db.collection("usuarios_finanzas").document(uid)

        // 1. Buscar el movimiento asociado en el historial para mantener sincronía con el Web Autopilot
        userRef.collection("movimientos")
            .whereEqualTo("categoria", "Deuda")
            .whereEqualTo("descripcion", "${debt.tipo}: ${debt.nombre}")
            .get()
            .addOnSuccessListener { snapshots ->
                db.runTransaction { transaction ->
                    val updates = mutableMapOf<String, Any>(
                        "deudas" to FieldValue.increment(-debt.montoTotal),
                        "ultimaActualizacion" to System.currentTimeMillis()
                    )

                    // REBALANCING: Si era préstamo, restamos del saldo y de los ingresos totales
                    if (debt.tipo == "Préstamo") {
                        updates["saldo"] = FieldValue.increment(-debt.montoTotal)
                        updates["ingresos"] = FieldValue.increment(-debt.montoTotal)
                    }

                    // 2. Eliminar los movimientos encontrados en el historial
                    for (doc in snapshots) {
                        transaction.delete(doc.reference)
                    }

                    // 3. Actualizar el documento principal y borrar la deuda
                    transaction.update(userRef, updates)
                    transaction.delete(userRef.collection("deudas").document(debt.id))
                }.addOnSuccessListener {
                    Toast.makeText(this, "Deuda y movimientos eliminados", Toast.LENGTH_SHORT).show()
                }.addOnFailureListener {
                    Toast.makeText(this, "Error al sincronizar eliminación", Toast.LENGTH_SHORT).show()
                }
            }
    }
}
