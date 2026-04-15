package com.wccslic.finanzainteligente

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

class HomeFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private val db = FirebaseFirestore.getInstance()
    
    private lateinit var rvHomeHistory: RecyclerView
    private var movementAdapter: MovementAdapter? = null
    private val movementList = mutableListOf<Movement>()
    private val standardMovements = mutableListOf<Movement>()
    private val debtMovements = mutableListOf<Movement>()
    
    private lateinit var chartsViewPager: ViewPager2
    private lateinit var chartPagerAdapter: ChartPagerAdapter
    
    private var financialListener: ListenerRegistration? = null
    private var historyListener: ListenerRegistration? = null
    private var deudasListener: ListenerRegistration? = null

    private var totalMensualDeudas = 0.0
    private var serviciosRecurrentes = 0.0
    private var saldoGross = 0.0
    private var ingresosTotal = 0.0
    private var gastosManualTotal = 0.0
    private var deudaCapitalTotal = 0.0

    private var isFinancialLoaded = false
    private var isHistoryLoaded = false
    private var isDeudasLoaded = false

    private val categories = arrayOf("Comida", "Transporte", "Vivienda", "Salud", "Entretenimiento", "Sueldo", "Venta", "Educación", "Servicios", "Otros")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        auth = FirebaseAuth.getInstance()
        
        chartsViewPager = view.findViewById(R.id.chartsViewPager)
        chartPagerAdapter = ChartPagerAdapter()
        chartsViewPager.adapter = chartPagerAdapter
        
        rvHomeHistory = view.findViewById(R.id.rvHomeHistory)
        rvHomeHistory.layoutManager = LinearLayoutManager(context)
        movementAdapter = MovementAdapter(movementList) { movement ->
            showDeleteConfirmation(movement)
        }
        rvHomeHistory.adapter = movementAdapter
        
        setupInputSection(view)

        view.findViewById<MaterialCardView>(R.id.cardIngresos).setOnClickListener {
            val intent = Intent(requireContext(), BreakdownActivity::class.java)
            intent.putExtra("FILTER_TYPE", "Ingreso")
            startActivity(intent)
        }
        view.findViewById<MaterialCardView>(R.id.cardGastos).setOnClickListener {
            val intent = Intent(requireContext(), BreakdownActivity::class.java)
            intent.putExtra("FILTER_TYPE", "Gasto")
            startActivity(intent)
        }

        return view
    }

    override fun onStart() {
        super.onStart()
        startListening()
    }

    override fun onStop() {
        super.onStop()
        financialListener?.remove()
        historyListener?.remove()
        deudasListener?.remove()
    }

    private fun startListening() {
        val uid = auth.currentUser?.uid ?: return
        val userRef = db.collection("usuarios_finanzas").document(uid)

        financialListener = userRef.addSnapshotListener { document, _ ->
            if (document != null && document.exists() && isAdded) {
                saldoGross = document.getDouble("saldo") ?: 0.0
                // We keep reading these as defaults, but we will recalculate from collections below
                serviciosRecurrentes = document.getDouble("servicios") ?: 0.0
                
                isFinancialLoaded = true
                updateUI()
            }
        }

        deudasListener = userRef.collection("deudas").addSnapshotListener { snapshots, _ ->
            if (snapshots != null && isAdded) {
                debtMovements.clear()
                var sumDeudas = 0.0
                val calendar = Calendar.getInstance()
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                val startOfMonth = calendar.timeInMillis

                for (doc in snapshots) {
                    val monto = doc.getDouble("pagoMensual") ?: 0.0
                    val nombre = doc.getString("nombre") ?: "Deuda"
                    if (monto > 0) {
                        sumDeudas += monto
                        debtMovements.add(Movement(
                            id = "recurring_debt_${doc.id}",
                            monto = monto,
                            tipo = "Gasto",
                            categoria = "Deuda",
                            descripcion = "Pago mensual: $nombre",
                            fecha = startOfMonth,
                            isDeuda = true
                        ))
                    }
                }
                totalMensualDeudas = sumDeudas
                isDeudasLoaded = true
                refreshMovementList()
                updateUI()
            }
        }

        historyListener = userRef.collection("movimientos")
            .orderBy("fecha", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, _ ->
                if (snapshots != null && isAdded) {
                    standardMovements.clear()
                    var sumIng = 0.0
                    var sumGas = 0.0
                    
                    val objects = snapshots.toObjects(Movement::class.java)
                    objects.forEachIndexed { index, mov ->
                        val m = mov.copy(id = snapshots.documents[index].id)
                        standardMovements.add(m)
                        
                        if (m.tipo == "Ingreso") {
                            sumIng += m.monto
                        } else {
                            sumGas += m.monto
                        }
                    }
                    ingresosTotal = sumIng
                    gastosManualTotal = sumGas
                    
                    isHistoryLoaded = true
                    refreshMovementList()
                    updateUI()
                }
            }
    }

    private fun refreshMovementList() {
        if (!isHistoryLoaded || !isDeudasLoaded) return

        val finalHistory = mutableListOf<Movement>()
        finalHistory.addAll(standardMovements)
        finalHistory.addAll(debtMovements)

        if (serviciosRecurrentes > 0) {
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.DAY_OF_MONTH, 1)
            finalHistory.add(Movement(
                id = "recurring_services",
                monto = serviciosRecurrentes,
                tipo = "Gasto",
                categoria = "Servicios",
                descripcion = "Servicios recurrentes mensuales",
                fecha = calendar.timeInMillis,
                isDeuda = false
            ))
        }

        val sorted = finalHistory.sortedByDescending { it.fecha }
        movementList.clear()
        movementList.addAll(sorted)
        movementAdapter?.notifyDataSetChanged()
        
        if (isFinancialLoaded) {
            val totalGasFactored = gastosManualTotal + totalMensualDeudas + serviciosRecurrentes
            chartPagerAdapter.setData(ingresosTotal, totalGasFactored, deudaCapitalTotal, movementList)
        }
    }

    private fun updateUI() {
        if (!isAdded) return
        view?.let { v ->
            val tvSaldo = v.findViewById<TextView>(R.id.tvSaldo)
            val tvIngresos = v.findViewById<TextView>(R.id.tvIngresos)
            val tvGastos = v.findViewById<TextView>(R.id.tvGastos)
            val tvWelcome = v.findViewById<TextView>(R.id.tvWelcome)
            
            val format = NumberFormat.getCurrencyInstance(Locale("es", "MX"))
            
            // Available balance logic: Gross Balance - Monthly Obligations
            val availableBalance = saldoGross - totalMensualDeudas - serviciosRecurrentes
            
            tvSaldo.text = format.format(availableBalance)
            tvIngresos.text = format.format(ingresosTotal)
            tvGastos.text = format.format(gastosManualTotal + totalMensualDeudas + serviciosRecurrentes)
            
            auth.currentUser?.let { tvWelcome.text = "¡Hola, ${it.displayName ?: "Usuario"}!" }
            
            refreshMovementList()
        }
    }

    private fun setupInputSection(view: View) {
        val etAmt = view.findViewById<TextInputEditText>(R.id.etQuickAmount)
        val etDesc = view.findViewById<TextInputEditText>(R.id.etQuickDescription)
        val btnInc = view.findViewById<MaterialButton>(R.id.btnAddIncome)
        val btnExp = view.findViewById<MaterialButton>(R.id.btnAddExpense)
        val cat = view.findViewById<AutoCompleteTextView>(R.id.autoCompleteCategory)
        
        cat.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, categories))

        btnInc.setOnClickListener { processInput(etAmt, etDesc, cat, true) }
        btnExp.setOnClickListener { processInput(etAmt, etDesc, cat, false) }
        view.findViewById<MaterialButton>(R.id.btnDeuda).setOnClickListener {
            startActivity(Intent(requireContext(), DeudaActivity::class.java))
        }
        view.findViewById<MaterialButton>(R.id.btnSignOut).setOnClickListener { signOut() }
    }

    private fun processInput(etAmt: TextInputEditText, etDesc: TextInputEditText, cat: AutoCompleteTextView, isInc: Boolean) {
        val amountStr = etAmt.text.toString()
        val amount = try {
            amountStr.replace("""[$,]""".toRegex(), "").toDouble()
        } catch (e: Exception) {
            0.0
        }

        if (amount > 0) {
            val uid = auth.currentUser!!.uid
            val userRef = db.collection("usuarios_finanzas").document(uid)
            
            val mov = hashMapOf(
                "monto" to amount,
                "tipo" to if (isInc) "Ingreso" else "Gasto",
                "categoria" to if (cat.text.isEmpty()) "General" else cat.text.toString(),
                "descripcion" to if (etDesc.text!!.isEmpty()) "Sin descripción" else etDesc.text.toString(),
                "fecha" to System.currentTimeMillis(),
                "isDeuda" to false
            )

            db.runTransaction { transaction ->
                val snapshot = transaction.get(userRef)
                val oldSaldo = snapshot.getDouble("saldo") ?: 0.0
                val oldIng = snapshot.getDouble("ingresos") ?: 0.0
                val oldGas = snapshot.getDouble("gastos") ?: 0.0
                
                val newSaldo = if (isInc) oldSaldo + amount else oldSaldo - amount
                val newIng = if (isInc) oldIng + amount else oldIng
                val newGas = if (!isInc) oldGas + amount else oldGas
                
                val newMovRef = userRef.collection("movimientos").document()
                transaction.set(newMovRef, mov)
                
                transaction.update(userRef, 
                    "saldo", newSaldo,
                    "ingresos", newIng,
                    "gastos", newGas,
                    "ultimaActualizacion", System.currentTimeMillis()
                )
            }.addOnSuccessListener {
                etAmt.text?.clear(); etDesc.text?.clear(); cat.text?.clear()
            }.addOnFailureListener {
                Toast.makeText(context, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showDeleteConfirmation(movement: Movement) {
        if (movement.id.startsWith("recurring_")) {
            Toast.makeText(context, "Los pagos recurrentes se gestionan desde Configuración", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Eliminar").setMessage("¿Deseas eliminar este registro?")
            .setPositiveButton("Eliminar") { _, _ ->
                val uid = auth.currentUser!!.uid
                val userRef = db.collection("usuarios_finanzas").document(uid)
                
                db.runTransaction { transaction ->
                    val snapshot = transaction.get(userRef)
                    val oldSaldo = snapshot.getDouble("saldo") ?: 0.0
                    val oldIng = snapshot.getDouble("ingresos") ?: 0.0
                    val oldGas = snapshot.getDouble("gastos") ?: 0.0
                    val oldDeuCapital = snapshot.getDouble("deudas") ?: 0.0
                    
                    var newSaldo = oldSaldo
                    var newIng = oldIng
                    var newGas = oldGas
                    var newDeuCapital = oldDeuCapital

                    if (movement.tipo == "Ingreso") {
                        newSaldo -= movement.monto
                        newIng -= movement.monto
                    } else {
                        newSaldo += movement.monto
                        newGas -= movement.monto
                    }

                    if (movement.isDeuda) {
                        newDeuCapital -= movement.monto
                    }

                    transaction.delete(userRef.collection("movimientos").document(movement.id))
                    transaction.update(userRef,
                        "saldo", newSaldo,
                        "ingresos", newIng,
                        "gastos", newGas,
                        "deudas", newDeuCapital,
                        "ultimaActualizacion", System.currentTimeMillis()
                    )
                }.addOnFailureListener {
                    Toast.makeText(context, "Error al eliminar", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun signOut() {
        auth.signOut()
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).requestIdToken(getString(R.string.default_web_client_id)).build()
        GoogleSignIn.getClient(requireActivity(), gso).signOut().addOnCompleteListener {
            startActivity(Intent(requireContext(), MainActivity::class.java))
            requireActivity().finish()
        }
    }
}
