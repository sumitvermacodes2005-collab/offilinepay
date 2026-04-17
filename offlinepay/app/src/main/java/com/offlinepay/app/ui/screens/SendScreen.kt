package com.offlinepay.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.offlinepay.app.data.repo.PaymentRepository
import com.offlinepay.app.util.Money
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SendVm @Inject constructor(
    private val repo: PaymentRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SendState())
    val state: StateFlow<SendState> = _state.asStateFlow()

    fun update(receiver: String, amount: String) {
        _state.value = _state.value.copy(receiverId = receiver, amountText = amount)
    }

    fun generate(senderId: String, onTokenJson: (String) -> Unit) {
        val s = _state.value
        val minor = Money.parseToMinor(s.amountText)
        if (s.receiverId.isBlank() || minor == null || minor <= 0) {
            _state.value = s.copy(error = "Enter a valid recipient and amount")
            return
        }
        viewModelScope.launch {
            _state.value = s.copy(loading = true, error = null)
            val token = repo.createOutgoing(senderId, s.receiverId, minor)
            // We re-serialize to the same canonical JSON format used in DB.
            val json = TokenJson.adapter.toJson(token)
            _state.value = SendState()  // reset
            onTokenJson(json)
        }
    }
}

data class SendState(
    val receiverId: String = "",
    val amountText: String = "",
    val loading: Boolean = false,
    val error: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendScreen(nav: NavController, vm: SendVm = hiltViewModel()) {
    val state by vm.state.collectAsState()
    val senderId = remember { "me@offlinepay" }   // demo identity

    Scaffold(topBar = { TopAppBar(title = { Text("Send payment") }) }) { padding ->
        Column(
            Modifier.padding(padding).padding(24.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = state.receiverId,
                onValueChange = { vm.update(it, state.amountText) },
                label = { Text("Recipient (mobile / user id)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.amountText,
                onValueChange = { vm.update(state.receiverId, it) },
                label = { Text("Amount") },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = KeyboardType.Decimal
                ),
                modifier = Modifier.fillMaxWidth()
            )
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                enabled = !state.loading,
                onClick = {
                    vm.generate(senderId) { tokenJson ->
                        TokenBuffer.value = tokenJson
                        nav.navigate(Routes.SHOW_QR)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Generate QR") }
        }
    }
}

/** Tiny in-memory hand-off so we don't pass a long string through nav args. */
object TokenBuffer { var value: String? = null }

object TokenJson {
    val adapter by lazy {
        com.squareup.moshi.Moshi.Builder()
            .add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
            .build()
            .adapter(com.offlinepay.app.data.model.PaymentToken::class.java)
    }
}
