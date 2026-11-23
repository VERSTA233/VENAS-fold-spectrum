package com.venas.foldspectrum.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.venas.foldspectrum.R

@Composable
internal fun ProfileLinks() {
    val context = LocalContext.current
    fun openBrowser(url: String) {
        // Resolve a browser rather than a verified GitHub/bilibili app link.
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            selector = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER)
        }
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "未找到可用浏览器", Toast.LENGTH_SHORT).show()
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp,
        androidx.compose.ui.Alignment.CenterHorizontally)) {
        IconButton(onClick = { openBrowser("https://github.com/VERSTA233/VENAS-fold-spectrum") },
            modifier = Modifier.size(48.dp)) {
            Icon(painterResource(R.drawable.ic_github), contentDescription = "GitHub 项目仓库（浏览器打开）",
                tint = Muted, modifier = Modifier.size(24.dp))
        }
        IconButton(onClick = { openBrowser("https://space.bilibili.com/34241475") },
            modifier = Modifier.size(48.dp)) {
            Icon(painterResource(R.drawable.ic_bilibili), contentDescription = "bilibili · 逆星朔影（浏览器打开）",
                tint = Muted, modifier = Modifier.size(24.dp))
        }
    }
}
