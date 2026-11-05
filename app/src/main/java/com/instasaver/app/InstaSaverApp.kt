package com.instasaver.app

import android.app.Application
import com.instasaver.app.data.local.InstaSaverDatabase
import com.instasaver.app.data.local.PreferencesDataStore
import com.instasaver.app.data.repository.DownloadRepository
import com.instasaver.app.data.repository.SettingsRepository
import com.instasaver.app.storage.MediaImporter
import com.instasaver.app.storage.MediaStoreSaver

class InstaSaverApp : Application() {

    lateinit var database: InstaSaverDatabase
        private set

    lateinit var preferencesDataStore: PreferencesDataStore
        private set

    lateinit var downloadRepository: DownloadRepository
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var mediaStoreSaver: MediaStoreSaver
        private set

    lateinit var mediaImporter: MediaImporter
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = InstaSaverDatabase.getInstance(this)
        preferencesDataStore = PreferencesDataStore(this)
        downloadRepository = DownloadRepository(this, database.downloadDao())
        settingsRepository = SettingsRepository(preferencesDataStore)
        mediaStoreSaver = MediaStoreSaver(this)
        mediaImporter = MediaImporter(this, mediaStoreSaver, database.downloadDao())
    }

    companion object {
        lateinit var instance: InstaSaverApp
            private set
    }
}
