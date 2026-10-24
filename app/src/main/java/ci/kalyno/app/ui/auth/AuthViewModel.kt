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

data class AuthUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val signedIn: Boolean = false,
    /** Profil retrouvé dans le cloud à la connexion (nouveau téléphone, réinstallation). */
    val restored: RemoteProfile? = null
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
        _state.update { it.copy(loading = true, error = null) }

        viewModelScope.launch {
            try {
                val rawNonce = UUID.randomUUID().toString()
                val idToken = requestGoogleIdToken(activity, sha256Hex(rawNonce))
                val session = api.signInWithGoogleIdToken(idToken, rawNonce)
                store.saveSession(session)

                // Compte déjà connu : on reprend classe, pseudo et parcours sur ce téléphone
                val remote = fetchProfileOrNull()
                if (remote != null) {
                    prefs.nickname = remote.nickname
                    prefs.level = remote.levelId
                    prefs.demoDone = true
                }
                _state.update { AuthUiState(signedIn = true, restored = remote) }
            } catch (e: GetCredentialCancellationException) {
                // L'élève a fermé la fenêtre : ce n'est pas une erreur
                _state.update { it.copy(loading = false) }
            } catch (e: NoCredentialException) {
                fail("Aucun compte Google trouvé sur ce téléphone. Ajoute-en un dans les réglages.")
            } catch (e: GetCredentialException) {
                Log.w(TAG, "Credential Manager: ${e.type}", e)
                fail("Connexion Google impossible. Réessaie dans un instant.")
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

    private fun fail(message: String) {
        _state.update { it.copy(loading = false, error = message) }
    }

    private companion object {
        const val TAG = "KalynoAuth"
    }
}
