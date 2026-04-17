package com.offlinepay.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.NavController
import com.offlinepay.app.data.model.TransactionEntity
import com.offlinepay.app.data.model.TxDirection
import com.offlinepay.app.data.repo.PaymentRepository
import com.offlinepay.app.util.Money
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@HiltViewModel
class HistoryVm @Inject constructor(repo: PaymentRepository) : ViewModel() {
    val txs: Flow<List<TransactionEntity>> = repo.observeAll()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(nav: NavController, vm: HistoryVm = hiltViewModel()) {
    val list by vm.txs.collectAsState(initial = emptyList())
    Scaffold(topBar = { TopAppBar(title = { Text("History") }) }) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (list.isEmpty()) {
                item { Text("No transactions yet.") }
            }
            items(list, key = { it.txId }) { tx -> TxRow(tx) }
        }
    }
}

@Composable
private fun TxRow(tx: TransactionEntity) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    if (tx.direction == TxDirection.OUTGOING)
                        "→ ${tx.receiverId}" else "← ${tx.senderId}",
                    fontWeight = FontWeight.SemiBold
                )
                AssistChip(onClick = {}, label = { Text(tx.status.name) })
            }
            Spacer(Modifier.height(4.dp))
            Text(Money.format(tx.amountMinor, tx.currency))
            tx.lastError?.let {
                Text("Error: $it", style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
