package com.collecter.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.collecter.app.data.AppDatabase
import com.collecter.app.data.VideoEntry
import com.collecter.app.data.VideoCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "collector.db").build()
        val dao = db.videoDao()
        val collector = VideoCollector(dao)
        val vm = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return CollectorViewModel(dao, collector) as T
            }
        })[CollectorViewModel::class.java]

        setContent {
            MaterialTheme {
                CollectorScreen(vm)
            }
        }
    }
}

class CollectorViewModel(
    private val dao: com.collecter.app.data.VideoDao,
    private val collector: VideoCollector
) : ViewModel() {
    var baseUrl by mutableStateOf("")
    var selector by mutableStateOf("")
    var startPage by mutableStateOf("1")
    var endPage by mutableStateOf("100")
    var status by mutableStateOf("대기 중")
    var isCollecting by mutableStateOf(false)
    var query by mutableStateOf("")
    val results = MutableStateFlow<List<VideoEntry>>(emptyList())
    val count = dao.count()

    init {
        viewModelScope.launch {
            dao.observeAll().collectLatest { results.value = it }
        }
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
        viewModelScope.launch { dao.deleteAll() }
    }
}

@androidx.compose.runtime.Composable
fun CollectorScreen(vm: CollectorViewModel) {
    val results by vm.results.collectAsStateCompat()
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Collecter", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(vm.baseUrl, { vm.baseUrl = it }, label = { Text("Base URL") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(vm.selector, { vm.selector = it }, label = { Text("CSS Selector (선택)") }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(vm.startPage, { vm.startPage = it }, label = { Text("시작") }, modifier = Modifier.weight(1f))
            OutlinedTextField(vm.endPage, { vm.endPage = it }, label = { Text("끝") }, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.collect() }, enabled = !vm.isCollecting) { Text("수집") }
            Button(onClick = { vm.clear() }) { Text("전체 삭제") }
        }
        Text(vm.status)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(vm.query, { vm.query = it }, label = { Text("검색") }, modifier = Modifier.fillMaxWidth())
        val filtered = if (vm.query.isBlank()) results else results.filter { it.title.contains(vm.query, ignoreCase = true) }
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(filtered) { item ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text("${item.title}\nPage ${item.page}", modifier = Modifier.padding(12.dp))
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun <T> kotlinx.coroutines.flow.StateFlow<T>.collectAsStateCompat(): androidx.compose.runtime.State<T> =
    androidx.compose.runtime.collectAsState(this)
