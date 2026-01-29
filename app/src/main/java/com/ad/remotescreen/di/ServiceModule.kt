package com.ad.remotescreen.di

import android.content.Context
import com.ad.remotescreen.capture.ScreenCaptureManager
import com.ad.remotescreen.control.ControlClient
import com.ad.remotescreen.service.NotificationHelper
import com.ad.remotescreen.webrtc.SignalingClient
import com.ad.remotescreen.webrtc.WebRTCClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

/**
 * Hilt module providing service-related dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object ServiceModule {
    
    @Provides
    @Singleton
    fun provideNotificationHelper(
        @ApplicationContext context: Context
    ): NotificationHelper {
        return NotificationHelper(context)
    }
    
    @Provides
    @Singleton
    fun provideScreenCaptureManager(
        @ApplicationContext context: Context
    ): ScreenCaptureManager {
        return ScreenCaptureManager(context)
    }
    
    @Provides
    @Singleton
    fun provideSignalingClient(
        okHttpClient: OkHttpClient
    ): SignalingClient {
        return SignalingClient(okHttpClient)
    }
    
    @Provides
    @Singleton
    fun provideWebRTCClient(
        @ApplicationContext context: Context,
        signalingClient: SignalingClient
    ): WebRTCClient {
        return WebRTCClient(context, signalingClient)
    }
    
    @Provides
    @Singleton
    fun provideControlClient(
        okHttpClient: OkHttpClient
    ): ControlClient {
        return ControlClient(okHttpClient)
    }
}
