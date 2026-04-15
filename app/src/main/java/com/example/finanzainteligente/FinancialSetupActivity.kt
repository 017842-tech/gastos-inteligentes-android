package com.wccslic.finanzainteligente

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.NumberFormat
import java.util.Locale

class FinancialSetupActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_financial_setup)

        auth = FirebaseAuth.getInstance()

        val etSaldo = findViewById<TextInputEditText>(R.id.etSaldo)
        val etIngresos = findViewById<TextInputEditText>(R.id.etIngresos)
        val etGastos = findViewById<TextInputEditText>(R.id.etGastos) 
        val etServicios = findViewById<TextInputEditText>(R.id.etServicios) 
        val etMinAlert = findViewById<TextInputEditText>(R.id.etMinAlert)
        val etMaxAlert = findViewById<TextInputEditText>(R.id.etMaxAlert)
        val btnGuardar = findViewById<MaterialButton>(R.id.btnGuardar)

        setupCurrencyFormatting(etSaldo)
        setupCurrencyFormatting(etIngresos)
        setupCurrencyFormatting(etGastos)
        setupCurrencyFormatting(etServicios)
        setupCurrencyFormatting(etMinAlert)
        setupCurrencyFormatting(etMaxAlert)

        loadCurrentData(etSaldo, etIngresos, etGastos, etServicios, etMinAlert, etMaxAlert)

        btnGuardar.setOnClickListener {
            val uid = auth.currentUser?.uid
            if (uid == null) return@setOnClickListener

            val inputSaldoInicial = parseCurrencyValue(etSaldo.text.toString())
            val inputIngresosMensuales = parseCurrencyValue(etIngresos.text.toString())
            val inputGastosHormiga = parseCurrencyValue(etGastos.text.toString())
            val serviciosRecurrentes = parseCurrencyValue(etServicios.text.toString())
            val minAlert = parseCurrencyValue(etMinAlert.text.toString())
            val maxAlert = parseCurrencyValue(etMaxAlert.text.toString())

            val now = System.currentTimeMillis()

            // CÁLCULO CORRECTO: El ingreso total incluye el dinero que ya tienes
            val totalIngresosCalculated = inputSaldoInicial + inputIngresosMensuales
            val totalGastosCalculated = inputGastosHormiga
            val saldoCalculated = totalIngresosCalculated - totalGastosCalculated

            val mainData = hashMapOf(
                "saldo" to saldoCalculated,
                "ingresos" to totalIngresosCalculated,
                "gastos" to totalGastosCalculated,
                "deudas" to 0.0,
                "servicios" to serviciosRecurrentes,
                "totalMensualDeudas" to 0.0,
                "minAlert" to minAlert,
                "maxAlert" to maxAlert,
                "ultimaActualizacion" to now
            )

            db.collection("usuarios_finanzas").document(uid).set(mainData)
                .addOnSuccessListener {
                    createInitialMovements(uid, inputSaldoInicial, inputIngresosMensuales, inputGastosHormiga, now)
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Error al guardar: ${it.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun createInitialMovements(uid: String, saldoInicial: Double, ingresos: Double, gastos: Double, timestamp: Long) {
        val userRef = db.collection("usuarios_finanzas").document(uid)
        
        userRef.collection("movimientos").limit(1).get()
            .addOnSuccessListener { snapshots ->
                if (snapshots.isEmpty) {
                    val batch = db.batch()
                    
                    if (saldoInicial > 0) {
                        val movSaldo = hashMapOf(
                            "monto" to saldoInicial,
                            "tipo" to "Ingreso",
                            "categoria" to "Saldo Inicial",
                            "descripcion" to "Dinero disponible al iniciar cuenta",
                            "fecha" to timestamp,
                            "isDeuda" to false,
                            "isSaldoInicial" to true
                        )
                        batch.set(userRef.collection("movimientos").document(), movSaldo)
                    }

                    if (ingresos > 0) {
                        val movIngreso = hashMapOf(
                            "monto" to ingresos,
                            "tipo" to "Ingreso",
                            "categoria" to "Ingresos Mensuales",
                            "descripcion" to "Configuración inicial de ingresos",
                            "fecha" to timestamp,
                            "isDeuda" to false,
                            "isSaldoInicial" to false
                        )
                        batch.set(userRef.collection("movimientos").document(), movIngreso)
                    }

                    if (gastos > 0) {
                        val movGasto = hashMapOf(
                            "monto" to gastos,
                            "tipo" to "Gasto",
                            "categoria" to "Gastos Hormiga",
                            "descripcion" to "Compras iniciales únicas",
                            "fecha" to timestamp,
                            "isDeuda" to false,
                            "isSaldoInicial" to false
                        )
                        batch.set(userRef.collection("movimientos").document(), movGasto)
                    }

                    batch.commit().addOnCompleteListener {
                        navigateToHome()
                    }
                } else {
                    navigateToHome()
                }
            }
            .addOnFailureListener { navigateToHome() }
    }

    private fun navigateToHome() {
        Toast.makeText(this, "Configuración completada", Toast.LENGTH_SHORT).show()
        val intent = Intent(this, HomeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun setupCurrencyFormatting(editText: TextInputEditText) {
        editText.addTextChangedListener(object : TextWatcher {
            private var current = ""
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (s.toString() != current) {
                    editText.removeTextChangedListener(this)
                    val cleanString = s.toString().replace("""[$,.]""".toRegex(), "")
                    if (cleanString.isNotEmpty()) {
                        val parsed = cleanString.toDouble()
                        val formatted = NumberFormat.getCurrencyInstance(Locale("es", "MX")).format(parsed / 100)
                        current = formatted
                        editText.setText(formatted)
                        editText.setSelection(formatted.length)
                    }
                    editText.addTextChangedListener(this)
                }
            }
        })
    }

    private fun parseCurrencyValue(value: String): Double {
        return try {
            // Elimina $, espacios y comas. Deja solo el número y el punto.
            val clean = value.replace("""[$\s,]""".toRegex(), "")
            clean.toDoubleOrNull() ?: 0.0
        } catch (e: Exception) {
            0.0
        }
    }

    private fun loadCurrentData(
        etSaldo: TextInputEditText, etIngresos: TextInputEditText, etGastos: TextInputEditText,
        etServicios: TextInputEditText, etMinAlert: TextInputEditText, etMaxAlert: TextInputEditText
    ) {
        val uid = auth.currentUser?.uid ?: return
        val format = NumberFormat.getCurrencyInstance(Locale("es", "MX"))
        
        db.collection("usuarios_finanzas").document(uid).get()
            .addOnSuccessListener { doc ->
                if (doc != null && doc.exists()) {
                    try {
                        etSaldo.setText(format.format(doc.getDouble("saldo") ?: 0.0))
                        etIngresos.setText(format.format(doc.getDouble("ingresos") ?: 0.0))
                        etGastos.setText(format.format(doc.getDouble("gastos") ?: 0.0))
                        etServicios.setText(format.format(doc.getDouble("servicios") ?: 0.0))
                        etMinAlert.setText(format.format(doc.getDouble("minAlert") ?: 0.0))
                        etMaxAlert.setText(format.format(doc.getDouble("maxAlert") ?: 100.0))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            .addOnFailureListener { e ->
                e.printStackTrace()
            }
    }
}
