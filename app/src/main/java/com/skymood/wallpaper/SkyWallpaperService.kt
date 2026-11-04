package com.skymood.wallpaper

import android.service.wallpaper.WallpaperService

class SkyWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = Engine()
}
