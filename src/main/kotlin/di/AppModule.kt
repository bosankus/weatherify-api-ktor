package di

import org.koin.dsl.module

/**
 * Main Koin module that includes all other modules.
 * This is the entry point for the dependency injection system.
 */
val appModule = module {
    includes(dataModule, domainModule)
}