package com.wccslic.finanzainteligente

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        auth = FirebaseAuth.getInstance()

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        findViewById<MaterialButton>(R.id.btnRegisterGoogle).setOnClickListener {
            signInWithGoogle()
        }

        findViewById<MaterialButton>(R.id.btnSignInGoogle).setOnClickListener {
            signInWithGoogle()
        }

        findViewById<MaterialButton>(R.id.btnPrivacyPolicy).setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://proyecto-integrado-467c0.web.app/politica-privacidad.html"))
            startActivity(intent)
        }
    }

    override fun onStart() {
        super.onStart()
        val currentUser = auth.currentUser
        if (currentUser != null) {
            checkSetupAndNavigate()
        }
    }

    private fun signInWithGoogle() {
        val signInIntent = googleSignInClient.signInIntent
        startActivityForResult(signInIntent, 100)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 100) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: ApiException) {
                Toast.makeText(this, "Error de Google: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    saveUserToFirestore()
                } else {
                    Toast.makeText(this, "Error en la autenticación con Firebase", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun saveUserToFirestore() {
        val user = auth.currentUser
        val uid = user?.uid
        val userMap = hashMapOf(
            "uid" to uid,
            "nombre" to user?.displayName,
            "email" to user?.email,
            "fechaRegistro" to System.currentTimeMillis()
        )

        if (uid != null) {
            db.collection("usuarios").document(uid).set(userMap)
                .addOnSuccessListener { checkSetupAndNavigate() }
                .addOnFailureListener { checkSetupAndNavigate() }
        } else {
            checkSetupAndNavigate()
        }
    }

    private fun checkSetupAndNavigate() {
        val uid = auth.currentUser?.uid ?: return
        
        db.collection("usuarios_finanzas").document(uid).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    startActivity(Intent(this, HomeActivity::class.java))
                } else {
                    startActivity(Intent(this, FinancialSetupActivity::class.java))
                }
                finish()
            }
            .addOnFailureListener {
                startActivity(Intent(this, HomeActivity::class.java))
                finish()
            }
    }
}
