package com.aditya.stride

import android.app.Application
import com.aditya.stride.data.Repository
import com.aditya.stride.notify.Notifications
import org.osmdroid.config.Configuration

class StrideApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)
        // Warm the database handle so the first screen does not stutter.
        Repository.get(this)
        // OpenStreetMap asks that every client identify itself.
        Configuration.getInstance().userAgentValue = packageName
        Configuration.getInstance().osmdroidBasePath = cacheDir
        val tiles = cacheDir.resolve("osm-tiles")
        tiles.mkdirs()
        Configuration.getInstance().osmdroidTileCache = tiles
    }
}
