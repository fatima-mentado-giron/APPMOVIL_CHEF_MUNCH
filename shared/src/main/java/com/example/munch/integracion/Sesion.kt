package com.example.munch.integracion

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun Sesion(rol: String, alSalir: () -> Unit = {}, contenido: @Composable () -> Unit) {
    if (FirebaseApp.getApps(androidx.compose.ui.platform.LocalContext.current).isEmpty()) {
        Column(Modifier.padding(24.dp)) { Text("Falta configurar Firebase. Añade google-services.json para esta aplicación y vuelve a compilar.") }
        return
    }
    val auth = remember { FirebaseAuth.getInstance() }
    var usuario by remember { mutableStateOf(auth.currentUser) }
    var autorizado by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var ocupado by remember { mutableStateOf(false) }
    var correo by rememberSaveable { mutableStateOf("") }
    var clave by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { usuario = it.currentUser }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }
    LaunchedEffect(usuario) {
        autorizado = false
        usuario?.let {
            try {
                val claims = it.getIdToken(true).await().claims
                autorizado = rol == "cliente" || claims[rol] == true
                if (!autorizado) error = "Tu cuenta no tiene el rol $rol. Solicítalo al responsable del proyecto."
            } catch (e: Exception) { error = e.localizedMessage ?: "No se pudo verificar la sesión" }
        }
    }
    if (autorizado && usuario != null) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(usuario?.email ?: "Sesión", Modifier.weight(1f).padding(8.dp))
                TextButton(onClick = { alSalir(); autorizado = false; auth.signOut() }) { Text("Salir") }
            }
            Box(Modifier.weight(1f)) { contenido() }
        }
    } else {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Munch · $rol", style = MaterialTheme.typography.headlineMedium)
            Text("Identifícate para acceder a tus pedidos y entregas.")
            if (usuario == null) {
                OutlinedTextField(correo, { correo = it }, label = { Text("Correo electrónico") }, singleLine = true)
                OutlinedTextField(clave, { clave = it }, label = { Text("Contraseña") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                fun entrar(crear: Boolean) {
                    scope.launch {
                        ocupado = true; error = ""
                        try {
                            if (crear) auth.createUserWithEmailAndPassword(correo.trim(), clave).await()
                            else auth.signInWithEmailAndPassword(correo.trim(), clave).await()
                            clave = ""
                        } catch (e: Exception) { error = e.localizedMessage ?: "Error de autenticación" }
                        ocupado = false
                    }
                }
                Button(onClick = { entrar(false) }, enabled = !ocupado && correo.isNotBlank() && clave.isNotBlank()) { Text("Entrar") }
                if (rol == "cliente") OutlinedButton(onClick = { entrar(true) }, enabled = !ocupado && clave.length >= 6) { Text("Crear cuenta de cliente") }
            } else {
                TextButton(onClick = { alSalir(); auth.signOut() }) { Text("Cerrar sesión") }
            }
            if (ocupado) CircularProgressIndicator()
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        }
    }
}
