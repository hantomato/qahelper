package com.md.qahelper.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.md.qahelper.QaHelper
import com.md.qahelper.ModuleTest
import com.md.qahelper.sample.ui.theme.QaHelperTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            QaHelperTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    TestButtonScreen(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
        ModuleTest.testLog()
    }
}

@Composable
fun TestButtonScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "테스트",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 20.dp)
        )

        // [1] 로그 테스트
        TestActionButton(text = "btn1") {
            QaHelper.start(context)
        }

        TestActionButton(text = "btn2") {
            QaHelper.stop(context)
        }

    }
}

@Composable
fun TestActionButton(
    text: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth() // 가로 꽉 채우기
    ) {
        Text(text = text)
    }
}

@Preview(showBackground = true)
@Composable
fun TestButtonScreenPreview() {
    QaHelperTheme {
        TestButtonScreen()
    }
}