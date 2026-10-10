package com.collecter.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.collecter.app.data.AppDatabase
import com.collecter.app.data.Collector
import com.collecter.app.data.VideoDao
import com.collecter.app.data.VideoEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dao = AppDatabase.get(this).videoDao()
        setContent {
            val vm: CollectorViewModel = viewModel(factory = CollectorViewModel.factory(dao))
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) { CollectorScreen(vm) }
            }
        }
    }
}

class CollectorViewModel(private val dao: VideoDao) : ViewModel() {
    private val collector = Collector(dao)
    private val queryState = MutableStateFlow("")

    var baseUrl by mutableStateOf("")
    var selector by mutableStateOf("")
    var startPage by mutableStateOf("1")
    var endPage by mutableStateOf("100")
    var status by mutableStateOf("대기 중")
    var isCollecting by mutableStateOf(false)
    var query by mutableStateOf("")
        private set

    val results = queryState
        .asStateFlow()
        .flatMapLatest { dao.search(it) }
    val count = dao.count()

    fun setQuery(value: String) {
        query = value
        queryState.value = value
    }

    fun collect() {
        val base = baseUrl.trim()
        val from = startPage.toIntOrNull()
        val to = endPage.toIntOrNull()
        if (base.isBlank() || from == null || to == null || from <= 0 || to < from) {
            status = "주소와 페이지 범위를 확인하세요."
            return
        }
        viewModelScope.launch {
            isCollecting = true
            status = "수집 시작..."
            try {
                collector.collect(base, from, to, selector) { page, found ->
                    status = "Page $page 수집 완료 · ${found}개"
                }
                status = "수집 완료"
            } catch (e: Exception) {
                status = "오류: ${e.message ?: "알 수 없는 오류"}"
            } finally {
                isCollecting = false
            }
        }
    }

    fun clear() {
        viewModelScope.launch { dao.clear() }
    }

    companion object {
        fun factory(dao: VideoDao) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = CollectorViewModel(dao) as T
        }
    }
}

@Composable
private fun CollectorScreen(vm: CollectorViewModel) {
    val items by vm.results.collectAsState(initial = emptyList())
    val total by vm.count.collectAsState(initial = 0)

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Collector", style = MaterialTheme.typography.headlineMedium)
        Text("페이지별 제목을 수집해 휴대폰에서 검색합니다.")

        OutlinedTextField(
            value = vm.baseUrl,
            onValueChange = { vm.baseUrl = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("사이트 기본 주소") },
            placeholder = { Text("https://example.com") },
            singleLine = true
        )
        OutlinedTextField(
            value = vm.selector,
            onValueChange = { vm.selector = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("제목 CSS 선택자 (선택)") },
            placeholder = { Text(".entry-title 또는 article h2") },
            singleLine = true
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = vm.startPage,
                onValueChange = { vm.startPage = it.filter(Char::isDigit) },
                modifier = Modifier.weight(1f),
                label = { Text("시작") },
                singleLine = true
            )
            OutlinedTextField(
                value = vm.endPage,
                onValueChange = { vm.endPage = it.filter(Char::isDigit) },
                modifier = Modifier.weight(1f),
                label = { Text("끝") },
                singleLine = true
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = vm::collect, enabled = !vm.isCollecting) { Text("수집 시작") }
            Button(onClick = vm::clear, enabled = !vm.isCollecting) { Text("DB 초기화") }
        }

        Text(vm.status)
        Text("저장된 제목: $total")

        OutlinedTextField(
            value = vm.query,
            onValueChange = vm::setQuery,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("제목 검색") },
            singleLine = true
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
            items(items) { item ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium)
                        Text("Page ${item.page}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
