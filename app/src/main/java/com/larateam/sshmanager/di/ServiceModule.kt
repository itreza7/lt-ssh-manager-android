package com.larateam.sshmanager.di

import com.larateam.sshmanager.service.AndroidForegroundServiceController
import com.larateam.sshmanager.ssh.ForegroundServiceController
import com.larateam.sshmanager.terminal.RealTerminalGateway
import com.larateam.sshmanager.terminal.TerminalGateway
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ServiceModule {
    @Binds
    abstract fun bindForegroundServiceController(
        impl: AndroidForegroundServiceController,
    ): ForegroundServiceController

    @Binds
    abstract fun bindTerminalGateway(impl: RealTerminalGateway): TerminalGateway
}
