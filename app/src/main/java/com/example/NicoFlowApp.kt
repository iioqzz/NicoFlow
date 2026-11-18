package com.example

import android.app.Application
import com.example.data.local.NicoDatabase
import com.example.data.remote.NicoApiClient
import com.example.data.repository.NicoRepository
import com.example.player.PlayerController

class NicoFlowApp : Application() {
    lateinit var apiClient: NicoApiClient
        private set
    lateinit var database: NicoDatabase
        private set
    lateinit var repository: NicoRepository
        private set
    lateinit var playerController: PlayerController
        private set

    override fun onCreate() {
        super.onCreate()
        apiClient = NicoApiClient()
        database = NicoDatabase.getInstance(this)
        repository = NicoRepository(this, database, apiClient)
        playerController = PlayerController(this)
    }
}
