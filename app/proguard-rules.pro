# v3.18: app ke classes ko na todo (reflection / Accessibility / WallpaperService)
-keep class com.divya.ai.** { *; }
-keep class * extends android.accessibilityservice.AccessibilityService { *; }
-keep class * extends android.service.wallpaper.WallpaperService { *; }
-dontwarn **
