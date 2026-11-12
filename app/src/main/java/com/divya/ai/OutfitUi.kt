package com.divya.ai

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Outfit chunne / badalne ka dialog. onChanged: outfit badalne par avatar refresh karne ke liye. */
@Composable
fun OutfitDialog(accent: Color, onChanged: () -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var tick by remember { mutableStateOf(0) }
    val files = remember(tick) { Outfits.list(ctx) }
    val sel = remember(tick) { Outfits.selected(ctx) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            Outfits.importFrom(ctx, uri)?.let { Outfits.select(ctx, it.name) }
            tick++; onChanged()
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Band karo") } },
        title = { Text("Divya ka outfit") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Apni image chuno. Ye sirf is phone mein save hogi.", fontSize = 12.sp)
                Button(onClick = { picker.launch("image/*") }) { Text("Nayi image chuno") }
                TextButton(onClick = { Outfits.select(ctx, null); tick++; onChanged() }) { Text("Asli avatar wapas lao") }
                if (files.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(files) { f ->
                            val bmp = remember(f.name) {
                                BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply { inSampleSize = 4 })?.asImageBitmap()
                            }
                            val on = f.name == sel
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                if (bmp != null) Image(
                                    bitmap = bmp, contentDescription = "outfit",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(72.dp).clip(RoundedCornerShape(10.dp))
                                        .border(if (on) 3.dp else 1.dp, if (on) accent else Color.Gray, RoundedCornerShape(10.dp))
                                        .clickable { Outfits.select(ctx, f.name); tick++; onChanged() }
                                )
                                if (on) TextButton(onClick = { Outfits.delete(ctx, f); tick++; onChanged() }) { Text("Hatao", fontSize = 11.sp) }
                            }
                        }
                    }
                }
            }
        }
    )
}
