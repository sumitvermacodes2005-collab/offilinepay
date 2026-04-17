package com.offlinepay.app.di

import android.content.Context
import androidx.room.Room
import com.offlinepay.app.data.db.AppDatabase
import com.offlinepay.app.data.db.TransactionDao
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    @Provides @Singleton
    fun provideDb(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "offlinepay.db").build()

    @Provides
    fun provideDao(db: AppDatabase): TransactionDao = db.transactionDao()

    @Provides @Singleton
    fun provideContext(@ApplicationContext ctx: Context): Context = ctx
}
