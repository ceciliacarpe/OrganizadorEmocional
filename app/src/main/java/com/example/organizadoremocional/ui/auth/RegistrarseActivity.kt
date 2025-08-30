package com.example.organizadoremocional.ui.auth

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.example.organizadoremocional.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/**
 * Regristro de un usuario
 */
class RegistrarseActivity : AppCompatActivity() {
    private val authViewModel: AuthViewModel by viewModels()

    private lateinit var tilNombre: TextInputLayout
    private lateinit var etNombre: TextInputEditText
    private lateinit var tilEmail: TextInputLayout
    private lateinit var etEmail: TextInputEditText
    private lateinit var tilPassword: TextInputLayout
    private lateinit var etPassword: TextInputEditText
    private lateinit var btnRegister: MaterialButton

    private var registrandose = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_registrarse)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        val root = findViewById<View>(R.id.rootContainer)

        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, systemBars.bottom)
            insets
        }

        initViews()
        setupListeners()
        observeAuthState()
    }

    private fun initViews() {
        tilNombre = findViewById(R.id.tilNombre)
        etNombre = findViewById(R.id.etNombre)
        tilEmail = findViewById(R.id.tilEmail)
        etEmail = findViewById(R.id.etEmail)
        tilPassword = findViewById(R.id.tilPassword)
        etPassword = findViewById(R.id.etPassword)
        btnRegister = findViewById(R.id.btnRegister)
    }

    private fun setupListeners() {
        btnRegister.setOnClickListener {
            val nombre = etNombre.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (validateInputs(nombre, email, password)) {
                registrandose = true
                authViewModel.registrarUsuario(email, password, nombre)
            }
        }
    }

    /**
     * Valida si los imputs son correctos
     * @param nombre nombre del usuario.
     * @param email email del usuario.
     * @param password contraseña de la nueva cuenta.
     */

    private fun validateInputs(nombre: String, email: String, password: String): Boolean {
        var isValid = true

        // Validar nombre
        if (nombre.isEmpty()) {
            tilNombre.error = "El nombre es requerido"
            isValid = false
        } else {
            tilNombre.error = null
        }

        // Validar email
        if (email.isEmpty()) {
            tilEmail.error = "El email es requerido"
            isValid = false
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.error = "Ingrese un email válido"
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
     * Observa los cambios en el estado de autenticación del usuario. Casos:
     *  - Se ha enviado verificación.
     *  - Se ha registrado con éxito: pasa a la pantalla de inicio de sesión.
     *  - Error.
     *
     */
    private fun observeAuthState() {
        authViewModel.authState.observe(this) { state ->
            when{
                registrandose && state is AuthViewModel.AuthState.VerificacionEnviada ->{
                    Toast.makeText(this, "Te hemos enviado un correo de verificación.",
                        Toast.LENGTH_LONG).show()
                    registrandose = false
                    finish()
                }
                registrandose && state is AuthViewModel.AuthState.Autenticado -> {
                    Toast.makeText(this, "Registro exitoso", Toast.LENGTH_SHORT).show()
                    registrandose = false
                }
                registrandose && state is AuthViewModel.AuthState.Error -> {
                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                    registrandose = false
                }
                else -> {
                    // No hacer nada para otros estados
                }
            }
        }
    }
}