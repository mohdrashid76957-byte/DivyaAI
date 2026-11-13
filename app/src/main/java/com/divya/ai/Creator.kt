package com.divya.ai

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/** v3.4: koi puche "tumhe kisne banaya?" to Divya jawab ke saath Rashid ji ki photo bhi dikhati hai (voice tool show_creator_photo + offline reply). */
object CreatorBus {
    var show by mutableStateOf(false)
    fun trigger() { Handler(Looper.getMainLooper()).post { show = true } }
}

@Composable
fun CreatorCard() {
    if (!CreatorBus.show) return
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(15000); CreatorBus.show = false }
    Dialog(onDismissRequest = { CreatorBus.show = false }) {
        val shape = RoundedCornerShape(22.dp)
        Column(
            Modifier.clip(shape).background(Color(0xFF07182A)).border(1.5.dp, Color(0xFF19F5D4), shape).padding(12.dp)
                .clickable { CreatorBus.show = false },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painterResource(R.drawable.creator_photo), "Rashid ji",
                Modifier.fillMaxWidth().heightIn(max = 460.dp).clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Crop, alignment = Alignment.TopCenter
            )
            Spacer(Modifier.height(10.dp))
            Text("Mere Creator - Rashid ji 💜", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text("Inhone mujhe zindagi di.", color = Color(0xFF8FB8C9), fontSize = 12.sp, textAlign = TextAlign.Center)
        }
    }
}
