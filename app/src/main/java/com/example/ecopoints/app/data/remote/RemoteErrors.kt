package com.example.ecopoints.app.data.remote
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.*
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException

fun Throwable.rethrowCancellation() {
    if (this is CancellationException && this !is TimeoutCancellationException) throw this
}
fun Throwable.remoteMessage(): String = when (this) {
    is TimeoutCancellationException -> "La conexión tardó demasiado. Revisa tu internet y reintenta."
    is FirebaseNetworkException -> "No hay conexión con Firebase. Revisa tu internet y reintenta."
    is FirebaseTooManyRequestsException -> "Demasiados intentos. Espera unos minutos y vuelve a intentarlo."
    is FirebaseAuthUserCollisionException -> "Ya existe una cuenta con ese correo. Inicia sesión o recupera tu contraseña."
    is FirebaseAuthWeakPasswordException -> "La contraseña no cumple los requisitos de seguridad."
    is FirebaseAuthInvalidCredentialsException, is FirebaseAuthInvalidUserException -> "Correo o contraseña incorrectos, o cuenta no disponible. Usa la contraseña que elegiste al registrarte en EcoPoints. Si no la recuerdas, pulsa ¿Olvidaste tu contraseña?"
    is FirebaseFirestoreException -> "No se pudo acceder al respaldo. Revisa internet y los permisos de Firestore."
    else -> "No se pudo completar la operación. Verifica la configuración de Firebase y vuelve a intentarlo."
}
