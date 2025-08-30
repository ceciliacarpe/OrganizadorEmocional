package com.example.organizadoremocional.ui.cuenta

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.organizadoremocional.R
import com.example.organizadoremocional.core.sync.CalendarioSync
import com.example.organizadoremocional.core.workers.CalendarioSyncWorker
import com.example.organizadoremocional.ui.auth.LoginActivity
import com.example.organizadoremocional.ui.auth.AuthViewModel
import com.example.organizadoremocional.ui.home.BaseActivity
import com.example.organizadoremocional.ui.home.HomeActivity
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.api.services.calendar.CalendarScopes
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Pantalla de gestión de la cuenta.
 */
class GestionarCuentaActivity : BaseActivity() {

    companion object {
        private const val PREFS_NAME            = "settings"
        private const val KEY_SYNC_ENABLED      = "calendarSyncEnabled"
        private const val KEY_SYNC_EMAIL        = "syncEmail"
        private const val RC_SIGN_IN_CAL        = 3001
        private const val REQUEST_AUTHORIZATION = 3002
        private const val REQ_GET_ACCOUNTS      = 4001
    }

    private val authViewModel: AuthViewModel by viewModels()
    private lateinit var googleSignInClient: GoogleSignInClient

    private lateinit var txtUsuario: TextView
    private lateinit var txtEmail: TextView
    private lateinit var switchSync: Switch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gestionar_cuenta)


        txtUsuario       = findViewById(R.id.txtUsuario)
        txtEmail         = findViewById(R.id.txtEmail)
        switchSync       = findViewById(R.id.switch_goo)
        val btnCerrarSes = findViewById<MaterialButton>(R.id.btnCerrarSesion)
        val btnCambiarPW = findViewById<MaterialButton>(R.id.btnCambiarPassword)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M &&
            checkSelfPermission(Manifest.permission.GET_ACCOUNTS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.GET_ACCOUNTS), REQ_GET_ACCOUNTS)
        }

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(com.google.android.gms.common.api.Scope(CalendarScopes.CALENDAR_EVENTS))
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        authViewModel.currentUser.observe(this) { user ->
            user?.let {
                txtEmail.text = it.email.orEmpty()
                val nombre = it.displayName
                if (!nombre.isNullOrBlank()) {
                    txtUsuario.text = nombre
                } else {
                    val uid = it.uid
                    val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    db.collection("usuarios").document(uid).get()
                        .addOnSuccessListener { doc ->
                            val nombreDB = doc.getString("nombre")
                            txtUsuario.text = nombreDB ?: "Sin nombre"
                        }
                        .addOnFailureListener {
                            txtUsuario.text = "Usuario"
                        }
                }
            }
        }

        //boton cerrar sesión.
        btnCerrarSes.setOnClickListener {
            googleSignInClient.signOut().addOnCompleteListener {
                authViewModel.cerrarSesion()
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                    .remove("currentMood")
                    .remove(KEY_SYNC_EMAIL)
                    .apply()

                startActivity(
                    Intent(this, LoginActivity::class.java)
                        .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK }
                )
                finish()
            }
        }


        // Botón recuperar contraseña
        btnCambiarPW.setOnClickListener {
            val email = txtEmail.text.toString().trim()
            if (email.isNotBlank()) authViewModel.recuperarContrasenia(email)
            else Toast.makeText(this, "Error: no hay correo", Toast.LENGTH_SHORT).show()
        }
        authViewModel.authState.observe(this) { s ->
            if (s is AuthViewModel.AuthState.RecuperacionEmailEnviado)
                Toast.makeText(this, "Correo de recuperación enviado.", Toast.LENGTH_LONG).show()
        }

        //Switch de sincronización con Google calendar.
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        switchSync.isChecked = prefs.getBoolean(KEY_SYNC_ENABLED, false)
        switchSync.setOnCheckedChangeListener { _, enabled ->
            prefs.edit().putBoolean(KEY_SYNC_ENABLED, enabled).apply()

            if (enabled) {
                val saved = prefs.getString(KEY_SYNC_EMAIL, null)
                if (saved != null) {
                    doInitialSync(saved)
                } else {
                    googleSignInClient
                        .revokeAccess()
                        .addOnCompleteListener {
                            startActivityForResult(googleSignInClient.signInIntent, RC_SIGN_IN_CAL)
                        }
                }
                scheduleMidnightSync()
            } else {
                prefs.edit().remove(KEY_SYNC_EMAIL).apply()
                cancelMidnightSync()
                Toast.makeText(this, "Sincronización de Calendar desactivada", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Sincronización al activadr el switch
     */
    private fun doInitialSync(email: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                CalendarioSync.syncAllPendingTasks(this@GestionarCuentaActivity, email)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@GestionarCuentaActivity,
                        "Calendario sincronizado correctamente", Toast.LENGTH_SHORT).show()
                }
            } catch (e: UserRecoverableAuthException) {
                // lanzarlo desde el hilo MAIN:
                e.intent?.let { intent ->
                    runOnUiThread {
                        startActivityForResult(intent, REQUEST_AUTHORIZATION)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    switchSync.isChecked = false
                    Toast.makeText(this@GestionarCuentaActivity,
                        "Error al sincronizar: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            RC_SIGN_IN_CAL -> GoogleSignIn.getSignedInAccountFromIntent(data)
                .addOnSuccessListener { acct ->
                    val email = acct.account?.name ?: return@addOnSuccessListener
                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                        .edit().putString(KEY_SYNC_EMAIL, email).apply()
                    doInitialSync(email)
                }
                .addOnFailureListener {
                    switchSync.isChecked = false
                    Toast.makeText(this, "No se pudo conectar con Calendar", Toast.LENGTH_SHORT).show()
                }
            REQUEST_AUTHORIZATION -> {
                // tras permiso de Calendar
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .getString(KEY_SYNC_EMAIL, null)
                    ?.let { doInitialSync(it) }
            }
        }
    }

    private fun scheduleMidnightSync() {
        CalendarioSyncWorker.programarSincronizacionDiaria(this)
    }

    private fun cancelMidnightSync() {
        CalendarioSyncWorker.cancelarSincronizacion(this)
    }

    override fun onBackPressed() {
        super.onBackPressed()
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }
}
