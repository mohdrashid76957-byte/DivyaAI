package com.divya.ai

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.util.Log
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder

/**
 * v3.15: Divya Core live wallpaper. assets/wallpaper/divya_core_wallpaper.mp4 ko loop karta hai.
 * Muted, screen band/hidden ho to pause (battery bachti hai).
 */
/**
 * v3.17: Divya Core ki preview seedha kholta hai (system chooser se pehle).
 * Direct preview fail ho to chooser par fallback.
 */
fun openDivyaCore(ctx: Context) {
    val direct = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
        .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, ComponentName(ctx, DivyaLiveWallpaper::class.java))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try { ctx.startActivity(direct); return } catch (e: Exception) { Log.w("DivyaCore", "direct preview fail: ${e.message}") }
    try { ctx.startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    catch (e: Exception) { Log.e("DivyaCore", "chooser fail: ${e.message}") }
}

class DivyaLiveWallpaper : WallpaperService() {
    override fun onCreateEngine(): Engine = VideoEngine()

    inner class VideoEngine : Engine() {
        private var player: MediaPlayer? = null

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            startVideo(holder)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            try {
                if (visible) player?.start() else player?.pause()
            } catch (_: Exception) {}
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            release()
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            release()
            super.onDestroy()
        }

        private fun startVideo(holder: SurfaceHolder) {
            release()
            try {
                val afd = assets.openFd("wallpaper/divya_core_wallpaper.mp4")
                player = MediaPlayer().apply {
                    setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    afd.close()
                    setSurface(holder.surface)
                    isLooping = true
                    setOnErrorListener { _, what, extra -> Log.e("DivyaCore", "player error $what/$extra"); true }
                    setVolume(0f, 0f)
                    prepare()
                    start()
                }
            } catch (_: Exception) {
                release()
            }
        }

        private fun release() {
            try { player?.stop() } catch (_: Exception) {}
            try { player?.release() } catch (_: Exception) {}
            player = null
        }
    }
}
