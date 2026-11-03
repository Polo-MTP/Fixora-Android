package com.fixora.app.feature.account.data

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class AccountModule {

    // TODO(API-10): cambiar por la implementación que llame a la API cuando existan los endpoints.
    @Binds
    abstract fun bindAccountRepository(impl: SampleAccountRepository): AccountRepository
}
