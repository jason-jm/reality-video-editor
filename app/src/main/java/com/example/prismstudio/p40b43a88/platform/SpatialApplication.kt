package com.example.prismstudio.p40b43a88.platform

import android.app.Application
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.media3.common.util.UnstableApi
import com.example.prismstudio.p40b43a88.data.AppGraph
import com.pico.spatial.ui.foundation.dsl.launch
import com.example.prismstudio.p40b43a88.mainApp
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch as launchCoroutine

class SpatialApplication : Application() {
    private val scope = MainScope()

    @androidx.annotation.OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        launch(::mainApp)
        // Release round R4: leaving the app writes an edit still waiting for its autosave (700 ms after the last one), so
        // a system that reclaims the process soon after cannot lose it.
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) {
                    Log.w(TAG, "App in the background: saving the open project")
                    scope.launchCoroutine { AppGraph.from(this@SpatialApplication).session.flush() }
                }
            },
        )
    }

    private companion object {
        const val TAG = "PrismApp"
    }
}
