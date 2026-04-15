package com.wccslic.finanzainteligente

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

class AlertsSetupActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alerts_setup)

        auth = FirebaseAuth.getInstance()

        val etMinAlert = findViewById<TextInputEditText>(R.id.etMinAlert)
        val etMaxAlert = findViewById<TextInputEditText>(R.id.etMaxAlert)
        val btnGuardar = findViewById<MaterialButton>(R.id.btnGuardarAlertas)
        val btnVolver = findViewById<MaterialButton>(R.id.btnVolver)

        setupCurrencyFormatting(etMinAlert)
        setupCurrencyFormatting(etMaxAlert)

        loadAlertData(etMinAlert, etMaxAlert)

        btnGuardar.setOnClickListener {
            saveAlertData(etMinAlert, etMaxAlert)
        }

        btnVolver.setOnClickListener {
            finish()
        }
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
                        val formatted = NumberFormat.getCurrencyInstance(Locale.US).format(parsed / 100)
                        current = formatted
                        editText.setText(formatted)
                        editText.setSelection(formatted.length)
                    }
                    editText.addTextChangedListener(this)
                }
            }
        })
    }

    private fun loadAlertData(etMinAlert: TextInputEditText, etMaxAlert: TextInputEditText) {
        val uid = auth.currentUser?.uid ?: return
        val format = NumberFormat.getCurrencyInstance(Locale.US)
        db.collection("usuarios_finanzas").document(uid).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    etMinAlert.setText(format.format(doc.getDouble("minAlert") ?: 0.0))
                    etMaxAlert.setText(format.format(doc.getDouble("maxAlert") ?: 100.0))
                }
            }
    }

    private fun saveAlertData(etMinAlert: TextInputEditText, etMaxAlert: TextInputEditText) {
        val uid = auth.currentUser?.uid ?: return
        val updates = hashMapOf<String, Any>(
            "minAlert" to parseCurrencyValue(etMinAlert.text.toString()),
            "maxAlert" to parseCurrencyValue(etMaxAlert.text.toString())
        )

        db.collection("usuarios_finanzas").document(uid).update(updates)
            .addOnSuccessListener {
                Toast.makeText(this, "Alertas actualizadas", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error al actualizar alertas", Toast.LENGTH_SHORT).show()
            }
    }

    private fun parseCurrencyValue(value: String): Double {
        return value.replace("""[$,]""".toRegex(), "").toDoubleOrNull() ?: 0.0
    }
}
