package com.example.organizadoremocional.ui.auth

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.organizadoremocional.R
import com.example.organizadoremocional.repository.EstadoDeAnimoRepository
import com.example.organizadoremocional.ui.estadoanimo.EstadoDeAnimoActivity
import com.example.organizadoremocional.ui.home.HomeActivity
import com.example.organizadoremocional.ui.estadoanimo.EstadoDeAnimoViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Clase para el inicio de sesión
 */
class LoginActivity : AppCompatActivity() {
    private val authViewModel: AuthViewModel by viewModels()
    private val estadoDeAnimoViewModel: EstadoDeAnimoViewModel by viewModels()
    private val animoRepo = EstadoDeAnimoRepository()

    private lateinit var auth :FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private val RC_SIGN_IN = 9001

    private lateinit var tilEmail: TextInputLayout
    private lateinit var etEmail: TextInputEditText
    private lateinit var tilPassword: TextInputLayout
    private lateinit var etPassword: TextInputEditText
    private lateinit var btnLogin: MaterialButton
    private lateinit var btnRegister: MaterialButton
    private lateinit var ivGoogle: ImageView
    private lateinit var tvForgotPassword : TextView

    /**
     * Hace para que la barra superior del dispositivo se visualice transparente
     */
    private fun setbarraTransparente() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            val w = window
            w.setFlags(
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        val root = findViewById<View>(R.id.rootContainer)

        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, systemBars.bottom)
            insets
        }

        setbarraTransparente()
        initViews()
        inicioGoogleSignIn()
        setupListeners()
        observeAuthState()

        // Observa si el usuario ya definió estado de animo de hoy tras autenticarse
        estadoDeAnimoViewModel.existeHoy.observe(this) { existeHoy ->
            if (existeHoy) {
                val intent = Intent(this, HomeActivity::class.java)
                startActivity(intent)
            } else {
                val intent = Intent(this, EstadoDeAnimoActivity::class.java)
                startActivity(intent)
            }
        }
    }

    private fun initViews() {
        tilEmail = findViewById(R.id.tilEmail)
        etEmail = findViewById(R.id.etEmail)
        tilPassword = findViewById(R.id.tilPassword)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)
        btnRegister = findViewById(R.id.btnRegister)
        ivGoogle = findViewById(R.id.ivGoogle)
        tvForgotPassword = findViewById(R.id.tvForgotPassword)
    }

    private fun setupListeners() {
        // Botón de iniciar sesión
        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (validarInputs(email, password)) {
                authViewModel.iniciarSesion(email, password)
            }
        }

        // Botón de registro
        btnRegister.setOnClickListener {
            val intent = Intent(this, RegistrarseActivity::class.java)
            startActivity(intent)
        }

        // Botón olvidé mi contraseña
        tvForgotPassword.setOnClickListener{
            val email = etEmail.text.toString().trim()
            if(email.isBlank() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()){
                tilEmail.error = "Introduce un email válido"
            }else{
                tilEmail.error = null
                authViewModel.recuperarContrasenia(email)
            }
        }

        // Botón login de google
        ivGoogle.setOnClickListener{
            startActivityForResult(googleSignInClient.signInIntent, RC_SIGN_IN)
        }

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)!!
                inicioFirebaseConGoogle(account.idToken!!)
            } catch (e: ApiException) {
                Toast.makeText(this, "No se ha podido iniciar sesión con Google: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Configura el inicio de sesión con Google y la autenticación de Firebase.
     *
     */
    private fun inicioGoogleSignIn() {
        auth = FirebaseAuth.getInstance()
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)
    }

    /**
     * Inicia sesión en Firebase usando las credenciales de Google.
     *
     * @param idToken Token de identificación obtenido tras el inicio de sesión con Google.
     */
    private fun inicioFirebaseConGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnSuccessListener {
                authViewModel.onGoogleSignInSuccess()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Auth Google falló: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    /**
     * Valida si los imputs son correctos
     * @param email correo electronico
     * @param password contraseña
     */
    private fun validarInputs(email: String, password: String): Boolean {
        var isValid = true

        // Validar email
        if (email.isEmpty()) {
            tilEmail.error = "El email es requerido"
            isValid = false
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.error = "Escriba un email válido"
            isValid = false
        } else {
            tilEmail.error = null
        }

        // Validar contraseña
        if (password.isEmpty()) {
            tilPassword.error = "La contraseña es requerida"
            isValid = false
        } else if (password.length < 6) {
            tilPassword.error = "La contraseña debe tener al menos 6 caracteres"
            isValid = false
        } else {
            tilPassword.error = null
        }

        return isValid
    }

    /**
     * Observa los cambios en el estado de autenticación del usuario.
     * Casos:
     *  - Usuario autenticado: guarda estado de ánimo de hoy.
     *  - Email no verificado
     *  - Verificación de email enviada.
     *  - Error
     *  - Recuperación contraseña.
     */
    private fun observeAuthState() {
        authViewModel.authState.observe(this) { state ->
            when (state) {
                is AuthViewModel.AuthState.Autenticado -> {
                    lifecycleScope.launch {
                        val estadosHoy = animoRepo.getHistorialDeHoySuspend()
                        val mood = estadosHoy.lastOrNull()?.tipoEAnimo ?: "MOTIVADO"
                        getSharedPreferences("settings", MODE_PRIVATE)
                            .edit()
                            .putString("currentMood", mood.toString().uppercase())
                            .apply()
                        withContext(Dispatchers.Main) {
                            estadoDeAnimoViewModel.existeEstadoAHoy()
                        }
                    }
                }
                is AuthViewModel.AuthState.EmailNoVerificado->{
                    Toast.makeText(this, "Tu cuenta no está verificada. Por favor, revisa tu bandeja.",
                        Toast.LENGTH_LONG).show()
                }
                is AuthViewModel.AuthState.VerificacionEnviada -> {
                    Toast.makeText(this, "Correo de verificación enviado. Revisa tu bandeja.",
                        Toast.LENGTH_LONG).show()
                }
                is AuthViewModel.AuthState.Error -> {
                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                }
                is AuthViewModel.AuthState.RecuperacionEmailEnviado -> {
                    Toast.makeText(this, "Correo de recuperación enviado", Toast.LENGTH_LONG).show()
                }
                else -> { }
            }
        }
        authViewModel.checkUsuarioActual()
    }
}