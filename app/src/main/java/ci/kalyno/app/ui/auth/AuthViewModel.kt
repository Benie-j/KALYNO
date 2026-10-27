package ci.kalyno.app.ui.auth

import android.app.Activity
import android.app.Application
import android.util.Log
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ci.kalyno.app.data.AppPrefs
import ci.kalyno.app.data.auth.AuthException
import ci.kalyno.app.data.auth.AuthStore
import ci.kalyno.app.data.auth.SupabaseAuthApi
import ci.kalyno.app.data.auth.requestGoogleIdToken
import ci.kalyno.app.data.auth.sha256Hex
import ci.kalyno.app.data.cloud.CloudSync
import ci.kalyno.app.data.cloud.RemoteProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.UUID

enum class EmailStage { ENTER_EMAIL, ENTER_CODE }

data class AuthUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val signedIn: Boolean = false,
    /** Profil retrouvé dans le cloud à la connexion (nouveau téléphone, réinstallation). */
    val restored: RemoteProfile? = null,
    /** Étape du flux e-mail (null = l'élève n'a pas encore choisi cette voie). */
    val emailStage: EmailStage? = null,
    val pendingEmail: String = ""
)

class AuthViewModel(app: Application) : AndroidViewModel(app) {

    private val store = AuthStore(app)
    private val api = SupabaseAuthApi()
    private val cloud = CloudSync(app)
    private val prefs = AppPrefs(app)

    private val _state = MutableStateFlow(AuthUiState(signedIn = store.isSignedIn()))
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    /** À appeler une fois le profil restauré appliqué, pour ne pas le réappliquer (rotation d'écran). */
    fun consumeRestored() {
        _state.update { it.copy(restored = null) }
    }

    /** Inscription et connexion : le compte est créé à la première connexion Google. */
    fun signInWithGoogle(activity: Activity) {
        if (_state.value.loading) return
        _state.update { it.copy(loading = true, error = "Étape 1/3 : ouverture de Google…") }

        viewModelScope.launch {
            try {
                val rawNonce = UUID.randomUUID().toString()
                val idToken = requestGoogleIdToken(activity, sha256Hex(rawNonce))
                _state.update { it.copy(error = "Étape 2/3 : échange avec Supabase…") }
                val session = api.signInWithGoogleIdToken(idToken, rawNonce)
                store.saveSession(session)
                _state.update { it.copy(error = "Étape 3/3 : compte créé, chargement du profil…") }

                // Compte déjà connu : on reprend classe, pseudo et parcours sur ce téléphone
                val remote = fetchProfileOrNull()
                if (remote != null) {
                    prefs.nickname = remote.nickname
                    prefs.level = remote.levelId
                    prefs.demoDone = true
                }
                _state.update { AuthUiState(signedIn = true, restored = remote) }
            } catch (e: GetCredentialCancellationException) {
                // Fenêtre fermée par l'élève, ou compte refusé par Google (signature de l'app non reconnue)
                Log.w(TAG, "Credential Manager annulé: ${e.message}", e)
                fail("Connexion Google interrompue (annulée, ou signature de l'app non reconnue par Google).")
            } catch (e: NoCredentialException) {
                fail("Aucun compte Google trouvé sur ce téléphone. Ajoute-en un dans les réglages.")
            } catch (e: GetCredentialException) {
                Log.w(TAG, "Credential Manager: ${e.type}", e)
                fail("Connexion Google impossible (${e.javaClass.simpleName}). Réessaie dans un instant.")
            } catch (e: AuthException) {
                fail(e.message ?: "Connexion impossible.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                fail("Pas de connexion internet. Vérifie ton réseau et réessaie.")
            } catch (e: Exception) {
                Log.w(TAG, "Erreur inattendue", e)
                fail("Une erreur est survenue. Réessaie.")
            }
        }
    }

    private suspend fun fetchProfileOrNull(): RemoteProfile? = try {
        cloud.fetchProfile()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Profil cloud indisponible", e)
        null
    }

    /** Déconnexion : efface la session locale. Le mode invité reste disponible. */
    fun signOut() {
        store.signOut()
        _state.value = AuthUiState(signedIn = false)
    }

    fun fail(message: String) {
        _state.update { it.copy(loading = false, error = message) }
    }

    /** Démarre (ou relance) le flux e-mail : demande une adresse. */
    fun startEmailFlow() {
        _state.update { it.copy(emailStage = EmailStage.ENTER_EMAIL, error = null) }
    }

    /** Abandonne le flux e-mail et revient aux deux boutons (e-mail / Google). */
    fun cancelEmailFlow() {
        _state.update { it.copy(emailStage = null, pendingEmail = "", error = null) }
    }

    /** Envoie le code à l'adresse saisie. */
    fun sendEmailCode(email: String) {
        if (_state.value.loading) return
        val clean = email.trim()
        if (!clean.contains("@") || clean.length < 5) {
            _state.update { it.copy(error = "Adresse e-mail invalide.") }
            return
        }
        _state.update { it.copy(loading = true, error = null, pendingEmail = clean) }
        viewModelScope.launch {
            try {
                api.sendEmailOtp(clean)
                _state.update { it.copy(loading = false, emailStage = EmailStage.ENTER_CODE) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: AuthException) {
                fail(e.message ?: "Envoi du code impossible.")
            } catch (e: IOException) {
                fail("Pas de connexion internet. Vérifie ton réseau et réessaie.")
            } catch (e: Exception) {
                Log.w(TAG, "Erreur envoi OTP", e)
                fail("Une erreur est survenue. Réessaie.")
            }
        }
    }

    /** Vérifie le code reçu et ouvre la session (même logique de reprise de profil que Google). */
    fun verifyEmailCode(code: String) {
        if (_state.value.loading) return
        val email = _state.value.pendingEmail
        val clean = code.trim()
        if (clean.length < 4) {
            _state.update { it.copy(error = "Code incomplet.") }
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val session = api.verifyEmailOtp(email, clean)
                store.saveSession(session)
                val remote = fetchProfileOrNull()
                if (remote != null) {
                    prefs.nickname = remote.nickname
                    prefs.level = remote.levelId
                    prefs.demoDone = true
                }
                _state.update { AuthUiState(signedIn = true, restored = remote) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: AuthException) {
                fail(e.message ?: "Code incorrect.")
            } catch (e: IOException) {
                fail("Pas de connexion internet. Vérifie ton réseau et réessaie.")
            } catch (e: Exception) {
                Log.w(TAG, "Erreur vérification OTP", e)
                fail("Une erreur est survenue. Réessaie.")
            }
        }
    }

    private companion object {
        const val TAG = "KalynoAuth"
    }
}
