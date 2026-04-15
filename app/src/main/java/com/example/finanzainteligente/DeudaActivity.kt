package com.wccslic.finanzainteligente

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import java.text.NumberFormat
import java.util.Locale

class DeudaActivity : AppCompatActivity() {

    private lateinit var etNombre: EditText
    private lateinit var etMonto: EditText
    private lateinit var etInteres: EditText
    private lateinit var etMeses: EditText
    private lateinit var etPago: EditText
    private lateinit var tvMensualidadPreview: TextView
    private lateinit var autoCompleteTipoDeuda: AutoCompleteTextView
    private lateinit var btnGuardar: Button

    private val tiposDeuda = arrayOf("Préstamo", "Tarjeta de Crédito", "Meses sin Intereses (MSI)", "Apartado (Layaway)", "Otro")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_deuda)

        autoCompleteTipoDeuda = findViewById(R.id.autoCompleteTipoDeuda)
        etNombre = findViewById(R.id.etNombre)
        etMonto = findViewById(R.id.etMonto)
        etInteres = findViewById(R.id.etInteres)
        etMeses = findViewById(R.id.etMeses)
        etPago = findViewById(R.id.etPago)
        tvMensualidadPreview = findViewById(R.id.tvMensualidadPreview)
        btnGuardar = findViewById(R.id.btnGuardar)

        setupCurrencyFormatting(etMonto)
        setupCurrencyFormatting(etPago)
        
        setupAutoCalculation()

        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, tiposDeuda)
        autoCompleteTipoDeuda.setAdapter(adapter)
        autoCompleteTipoDeuda.setText(tiposDeuda[0], false)

        btnGuardar.setOnClickListener {
            guardarDeuda()
        }
    }

    private fun setupAutoCalculation() {
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                calculateMensualidad()
            }
        }
        etMonto.addTextChangedListener(watcher)
        etInteres.addTextChangedListener(watcher)
        etMeses.addTextChangedListener(watcher)
    }

    private fun calculateMensualidad() {
        val monto = parseCurrencyValue(etMonto.text.toString())
        val interes = etInteres.text.toString().toDoubleOrNull() ?: 0.0
        val meses = etMeses.text.toString().toIntOrNull() ?: 12
        
        if (monto > 0 && meses > 0) {
            val totalConInteres = monto * (1 + (interes / 100))
            val mensualidad = totalConInteres / meses
            
            val format = NumberFormat.getCurrencyInstance(Locale("es", "MX"))
            tvMensualidadPreview.text = "Mensualidad: ${format.format(mensualidad)}"
            
            // Sync with hidden etPago to maintain compatibility with existing guardarDeuda logic
            val cleanFormatted = format.format(mensualidad)
            etPago.setText(cleanFormatted)
        } else {
            tvMensualidadPreview.text = "Mensualidad: $0.00"
        }
    }

    private fun setupCurrencyFormatting(editText: EditText) {
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
            value.replace("""[$,]""".toRegex(), "").toDoubleOrNull() ?: 0.0
        } catch (e: Exception) {
            0.0
        }
    }

    private fun guardarDeuda() {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Toast.makeText(this, "Usuario no autenticado", Toast.LENGTH_SHORT).show()
            return
        }

        val tipoSeleccionado = autoCompleteTipoDeuda.text.toString()
        val nombre = etNombre.text.toString()
        val monto = parseCurrencyValue(etMonto.text.toString())
        val interes = etInteres.text.toString().toDoubleOrNull()
        val pago = parseCurrencyValue(etPago.text.toString())

        if (nombre.isEmpty() || monto <= 0 || interes == null || pago <= 0) {
            Toast.makeText(this, "Completa todos los campos correctamente", Toast.LENGTH_SHORT).show()
            return
        }

        val db = FirebaseFirestore.getInstance()
        val userRef = db.collection("usuarios_finanzas").document(user.uid)
        val now = System.currentTimeMillis()

        val isPrestamo = tipoSeleccionado == "Préstamo"

        val debtData = hashMapOf(
            "tipo" to tipoSeleccionado,
            "nombre" to nombre,
            "montoTotal" to monto,
            "tasaInteres" to interes,
            "pagoMensual" to pago,
            "fecha" to now,
            "activa" to true
        )

        userRef.collection("deudas").add(debtData)
            .addOnSuccessListener { debtDoc ->
                val movimientoDeuda = hashMapOf(
                    "monto" to monto,
                    "tipo" to if (isPrestamo) "Ingreso" else "Gasto",
                    "categoria" to "Deuda",
                    "descripcion" to "$tipoSeleccionado: $nombre",
                    "fecha" to now,
                    "isDeuda" to true,
                    "relatedDebtId" to debtDoc.id,
                    "relatedMonthlyPayment" to pago
                )

                userRef.collection("movimientos").add(movimientoDeuda)
                    .addOnSuccessListener {
                        val updates = mutableMapOf<String, Any>(
                            "deudas" to FieldValue.increment(monto),
                            "ultimaActualizacion" to now
                        )

                        if (isPrestamo) {
                            // Los préstamos se consideran ingresos de efectivo (CASH IN)
                            updates["ingresos"] = FieldValue.increment(monto)
                            updates["saldo"] = FieldValue.increment(monto)
                        } else {
                            // Las compras a crédito (MSI, Tarjeta) NO restan del saldo disponible (CASH) inmediatamente
                            // solo incrementan la deuda total.
                        }

                        userRef.update(updates).addOnSuccessListener {
                            val mxnFormat = NumberFormat.getCurrencyInstance(Locale("es", "MX"))
                            Toast.makeText(this, "✅ $tipoSeleccionado registrado: ${mxnFormat.format(monto)}", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    }
            }
            .addOnFailureListener {
                Toast.makeText(this, "❌ Error al guardar", Toast.LENGTH_SHORT).show()
            }
    }
}
