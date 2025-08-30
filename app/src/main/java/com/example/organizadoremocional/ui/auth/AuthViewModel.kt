package com.example.organizadoremocional.ui.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.example.organizadoremocional.model.Usuario
import com.google.firebase.auth.FirebaseUser

/**
 * ViewModel encargado de gestionar la lógica de autenticación del usuario.
 */
class AuthViewModel : ViewModel() {

    //Configuración con Firestore
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private val _currentUser = MutableLiveData<FirebaseUser?>()
    val currentUser: LiveData<FirebaseUser?> = _currentUser

    //Estado autenticacion del usuario
    private val estadoAuth = MutableLiveData<AuthState>()
    val authState: LiveData<AuthState> get() = estadoAuth

    //Estados posibles de la autenticación
    sealed class AuthState{
        object Autenticado: AuthState()
        object Noautenticado: AuthState()
        object EmailNoVerificado: AuthState()
        object VerificacionEnviada: AuthState()
        object RecuperacionEmailEnviado: AuthState()
        class Error(val message: String): AuthState()
    }

    //verifica si hay usuario autenticado al iniciar
    init {
        _currentUser.value = auth.currentUser
        auth.addAuthStateListener { firebaseAuth ->
            val u = firebaseAuth.currentUser
            estadoAuth.value = when {
                u == null -> AuthState.Noautenticado
                u.isEmailVerified -> AuthState.Autenticado
                else -> AuthState.EmailNoVerificado
            }
        }
    }

    /**
     * Observa si está autenticado o no un usuario.
     */
    fun checkUsuarioActual(){
        if (auth.currentUser != null){
            estadoAuth.value = AuthState.Autenticado
        }else{
            estadoAuth.value = AuthState.Noautenticado
        }
    }


    /**
     * Registrar usuario.
     * @param email Correo electronico del usuario.
     * @param password Contraseña del usuario.
     * @param nombre Nombre del usuario.
     */
    fun registrarUsuario(email:String, password:String, nombre: String){
        auth.createUserWithEmailAndPassword(email,password)
            .addOnSuccessListener { result ->
                val user = result.user ?: return@addOnSuccessListener

                db.collection("usuarios").document(user.uid)
                    .set(Usuario(user.uid,nombre,email))
                    .addOnSuccessListener { user.sendEmailVerification()
                        .addOnSuccessListener { estadoAuth.postValue(AuthState.VerificacionEnviada) }
                        .addOnFailureListener { e -> estadoAuth.postValue(AuthState.Error("Error al enviar verificación: ${e.message}")) }
                    }
            .addOnFailureListener { e -> estadoAuth.postValue(AuthState.Error("Error al crear perfil: ${e.message}")) }
            }
            .addOnFailureListener { e -> estadoAuth.postValue(AuthState.Error("Error al registrar usuario: ${e.message}")) }
    }


    /**
     * Iniciar sesión.
     * @param email email del usuario.
     * @param password contraseña del usuario.
     */
    fun iniciarSesion(email:String, password:String){
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val user = auth.currentUser!!
                if(user.isEmailVerified){
                    estadoAuth.postValue(AuthState.Autenticado)
                }else{
                    estadoAuth.postValue(AuthState.EmailNoVerificado)
                }
                        }
            .addOnFailureListener { e-> estadoAuth.postValue(
                AuthState.Error(
                    e.message ?: "No se pudo iniciar sesión"
                )
            ) }

    }

    /**
     * Cerrar sesión.
     */
    fun cerrarSesion(){
        auth.signOut()
        estadoAuth.value = AuthState.Noautenticado

    }

    /**
     * Inicio de sesión con Google
     */
    fun onGoogleSignInSuccess() {
        val user = auth.currentUser!!
        val nombre = user.displayName ?: user.email!!.substringBefore('@')
        // Crear perfil en Firestore
        db.collection("usuarios").document(user.uid)
            .set(Usuario(user.uid, nombre, user.email!!))
            .addOnSuccessListener { _ -> estadoAuth.postValue(AuthState.Autenticado) }
            .addOnFailureListener { e -> estadoAuth.postValue(AuthState.Error(e.message ?: "")) }
    }

    /**
     * Recupera la contraseña a través del envio de un email.
     * @param email email de la cuenta a recuperar la contraseña.
     */
    fun recuperarContrasenia(email: String){
        auth.sendPasswordResetEmail(email).addOnSuccessListener { estadoAuth.postValue(AuthState.RecuperacionEmailEnviado) }
            .addOnFailureListener { e -> estadoAuth.postValue(
                AuthState.Error(
                    e.message ?: "Error recuperar contraseña"
                )
            ) }
    }
}