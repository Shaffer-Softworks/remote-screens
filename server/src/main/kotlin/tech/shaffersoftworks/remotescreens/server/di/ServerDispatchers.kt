package tech.shaffersoftworks.remotescreens.server.di

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** The process-wide IO dispatcher. Call sites take a [CoroutineDispatcher] so tests can substitute one. */
@Suppress("InjectDispatcher")
internal fun serverIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

/** The process-wide default dispatcher. Call sites take a [CoroutineDispatcher] so tests can substitute one. */
@Suppress("InjectDispatcher")
internal fun serverDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default
