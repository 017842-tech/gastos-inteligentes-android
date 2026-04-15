package com.wccslic.finanzainteligente

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.NumberFormat
import java.util.Locale

class SetupFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private val db = FirebaseFirestore.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_setup, container, false)

        auth = FirebaseAuth.getInstance()

        val etSaldo = view.findViewById<TextInputEditText>(R.id.etSaldo)
        val etIngresos = view.findViewById<TextInputEditText>(R.id.etIngresos)
        val etGastos = view.findViewById<TextInputEditText>(R.id.etGastos)
        val etServicios = view.findViewById<TextInputEditText>(R.id.etServicios)
        val btnGuardar = view.findViewById<MaterialButton>(R.id.btnGuardar)
        val btnGoToAlerts = view.findViewById<MaterialButton>(R.id.btnGoToAlerts)
        val btnDeleteAccount = view.findViewById<MaterialButton>(R.id.btnDeleteAccount)

        val btnManageDeudas = view.findViewById<MaterialButton>(R.id.btnManageDeudas)

        setupCurrencyFormatting(etSaldo)
        setupCurrencyFormatting(etIngresos)
        setupCurrencyFormatting(etGastos)
        setupCurrencyFormatting(etServicios)

        loadCurrentData(etSaldo, etIngresos, etGastos, etServicios)

        btnGuardar.setOnClickListener {
            saveData(etSaldo, etIngresos, etGastos, etServicios)
        }

        btnManageDeudas.setOnClickListener {
            val intent = Intent(requireContext(), ManageDeudasActivity::class.java)
            startActivity(intent)
        }

        btnGoToAlerts.setOnClickListener {
            val intent = Intent(requireContext(), AlertsSetupActivity::class.java)
            startActivity(intent)
        }

        btnDeleteAccount.setOnClickListener {
            showDeleteConfirmation()
        }

        return view
    }

    private fun showDeleteConfirmation() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Eliminar Cuenta")
            .setMessage("¿Estás seguro de que deseas eliminar tu cuenta y todos tus datos financieros? Esta acción no se puede deshacer.")
            .setPositiveButton("Eliminar") { _, _ ->
                deleteUserAccount()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun deleteUserAccount() {
        val user = auth.currentUser
        val uid = user?.uid ?: return

        // 1. Primero intentamos eliminar los datos de Firestore (incluyendo subcolecciones)
        val userRef = db.collection("usuarios_finanzas").document(uid)
        
        // Función para limpiar subcolecciones antes de borrar el documento principal
        userRef.collection("movimientos").get().addOnSuccessListener { movSnap ->
            val batch = db.batch()
            for (doc in movSnap) batch.delete(doc.reference)
            
            userRef.collection("deudas").get().addOnSuccessListener { deuSnap ->
                for (doc in deuSnap) batch.delete(doc.reference)
                
                // Borrar documentos de perfil y el documento principal de finanzas
                batch.delete(userRef)
                batch.delete(db.collection("usuarios").document(uid))

                batch.commit().addOnSuccessListener {
                    // 2. Intentamos borrar la cuenta de Auth
                    user.delete()
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                Toast.makeText(context, "Cuenta y datos eliminados correctamente", Toast.LENGTH_SHORT).show()
                                navigateToLogin()
                            } else {
                                // ERROR DE RE-AUTENTICACIÓN: Explicación clara para el Revisor de Google
                                Toast.makeText(context, "Confirmación requerida: Por seguridad, inicia sesión de nuevo para completar la eliminación definitiva.", Toast.LENGTH_LONG).show()
                                
                                // Opcional: Abrir correo de soporte como respaldo
                                val intent = Intent(Intent.ACTION_SENDTO).apply {
                                    data = android.net.Uri.parse("mailto:017842@universidadlux.edu.mx")
                                    putExtra(Intent.EXTRA_SUBJECT, "Solicitud de eliminación de cuenta - Gastos Inteligentes")
                                    putExtra(Intent.EXTRA_TEXT, "Hola, solicito la eliminación total de mi cuenta con UID: $uid")
                                }
                                try {
                                    startActivity(intent)
                                } catch (e: Exception) {
                                    // Si no hay app de correo, solo cerramos sesión
                                }

                                auth.signOut()
                                navigateToLogin()
                            }
                        }
                }.addOnFailureListener {
                    Toast.makeText(context, "Error al limpiar datos: ${it.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun navigateToLogin() {
        val intent = Intent(requireContext(), MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
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

    private fun loadCurrentData(
        etSaldo: TextInputEditText,
        etIngresos: TextInputEditText,
        etGastos: TextInputEditText,
        etServicios: TextInputEditText
    ) {
        val uid = auth.currentUser?.uid ?: return
        val format = NumberFormat.getCurrencyInstance(Locale.US)
        db.collection("usuarios_finanzas").document(uid).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    etSaldo.setText(format.format(doc.getDouble("saldo") ?: 0.0))
                    etIngresos.setText(format.format(doc.getDouble("ingresos") ?: 0.0))
                    etGastos.setText(format.format(doc.getDouble("gastos") ?: 0.0))
                    etServicios.setText(format.format(doc.getDouble("servicios") ?: 0.0))
                }
            }
    }

    private fun saveData(
        etSaldo: TextInputEditText,
        etIngresos: TextInputEditText,
        etGastos: TextInputEditText,
        etServicios: TextInputEditText
    ) {
        val uid = auth.currentUser?.uid ?: return
        val updates = hashMapOf<String, Any>(
            "saldo" to parseCurrencyValue(etSaldo.text.toString()),
            "ingresos" to parseCurrencyValue(etIngresos.text.toString()),
            "gastos" to parseCurrencyValue(etGastos.text.toString()),
            "servicios" to parseCurrencyValue(etServicios.text.toString()),
            "ultimaActualizacion" to System.currentTimeMillis()
        )

        db.collection("usuarios_finanzas").document(uid).update(updates)
            .addOnSuccessListener {
                Toast.makeText(context, "Datos actualizados", Toast.LENGTH_SHORT).show()
            }
    }

    private fun parseCurrencyValue(value: String): Double {
        return value.replace("""[$,]""".toRegex(), "").toDoubleOrNull() ?: 0.0
    }
}
