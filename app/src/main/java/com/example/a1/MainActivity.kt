package com.example.a1


import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import com.example.a1.ui.theme.A1Theme
import com.iflytek.sparkchain.core.LLM
import com.iflytek.sparkchain.core.LLMCallbacks
import com.iflytek.sparkchain.core.LLMConfig
import com.iflytek.sparkchain.core.LLMError
import com.iflytek.sparkchain.core.LLMEvent
import com.iflytek.sparkchain.core.LLMFactory
import com.iflytek.sparkchain.core.LLMResult
import com.iflytek.sparkchain.core.Memory
import com.iflytek.sparkchain.core.SparkChain
import com.iflytek.sparkchain.core.SparkChainConfig



class MainActivity : ComponentActivity() {
    // 定义响应状态
    private var responseState = mutableStateOf("")

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val config = SparkChainConfig.builder()
            .appID("")
            .apiKey("")//换成自己申请的
            .apiSecret("")
        SparkChain.getInst().init(applicationContext, config)
        val chatLLMConfig = LLMConfig.builder()
            .domain("generalv3")//注意要和url一一对应
            .url("wss://spark-api.xf-yun.com/v3.1/chat")
            .maxToken(2048)
            .temperature(0.7f)
            .topK(4)
        val memory = Memory.windowMemory(5)
        val chatLLM = LLMFactory.textGeneration(chatLLMConfig, memory)
        val llmCallbacks = object : LLMCallbacks {
            override fun onLLMResult(llmResult: LLMResult, usrContext: Any?) {
                responseState.value += llmResult.content
            }

            override fun onLLMEvent(event: LLMEvent, usrContext: Any?) {
                Log.d("LLMEvent", "Event ID: ${event.eventID}, Message: ${event.eventMsg}")
            }

            override fun onLLMError(error: LLMError, usrContext: Any?) {
                Log.e("LLMError", "Error Code: ${error.errCode}, Message: ${error.errMsg}")
            }
        }

        chatLLM.registerLLMCallbacks(llmCallbacks)

        setContent {
            A1Theme {
                ChatApp(chatLLM, responseState)
            }
        }
    }
}

data class Message(val sender: String, val content: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatApp(chatLLM: LLM, responseState: MutableState<String>) {
    var question by remember { mutableStateOf("") }
    val conversationList = remember { mutableStateListOf<Message>() }
    val scrollState = rememberLazyListState()
    val focusManager = LocalFocusManager.current

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("讯飞星火大模型") })
        },
        bottomBar = {
            // 输入栏在底部
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = question,
                    onValueChange = { question = it },
                    label = { Text("输入你的问题") },
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    singleLine = true
                )
                Button(
                    onClick = {
                        if (question.isNotEmpty()) {
                            // 添加新问题气泡
                            conversationList.add(Message("你", question))
                            // 清空AI的响应状态
                            responseState.value = ""
                            // 发送问题给模型
                            val userQuestion = question
                            chatLLM.arun(userQuestion)
                            question = "" // 清空输入框
                            focusManager.clearFocus()
                        }
                    }
                ) {
                    Text("发送")
                }
            }
        }
    ) { innerPadding ->
        // 显示对话记录
        LazyColumn(
            state = scrollState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            reverseLayout = false // 最新对话显示在底部
        ) {
            // 遍历每个消息
            items(conversationList.size) { index ->
                ChatBubble(message = conversationList[index])
            }
        }

        // 当有新的 AI 回复时，更新当前对话的回复气泡内容
        LaunchedEffect(responseState.value) {
            if (responseState.value.isNotEmpty()) {
                val lastIndex = conversationList.lastIndex
                if (lastIndex >= 0 && conversationList[lastIndex].sender == "AI") {
                    // 更新 AI 的消息内容
                    conversationList[lastIndex] = conversationList[lastIndex].copy(content = responseState.value)
                } else {
                    // 添加新的 AI 消息
                    conversationList.add(Message("AI", responseState.value))
                }
                // 自动滚动到最新消息
                scrollState.animateScrollToItem(conversationList.size - 1)
            }
        }
    }
}

@Composable
fun ChatBubble(message: Message) {
    val isUser = message.sender == "你"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start // 用户的消息靠右，AI 的消息靠左
    ) {
        Box(
            modifier = Modifier
                .background(
                    if (isUser) Color(0xFFBBDEFB) else Color(0xFFD1E7DD),
                    RoundedCornerShape(16.dp)
                )
                .padding(12.dp)
        ) {
            Text(text = "${message.sender}: ${message.content}", color = Color.Black)
        }
    }
}




