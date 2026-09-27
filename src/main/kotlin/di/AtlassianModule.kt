package di

import com.androidplay.core.secrets.getSecretValue
import config.AtlassianOAuthConfig
import data.atlassian.AtlassianApiClient
import data.atlassian.AtlassianOAuthClient
import data.atlassian.AtlassianTokenStore
import org.koin.dsl.module

/**
 * Koin wiring for Atlassian OAuth middleware (APE-10).
 */
val atlassianModule = module {
    single { AtlassianOAuthConfig.fromEnvironment() }

    single {
        val refresh = getSecretValue(AtlassianOAuthConfig.SECRET_REFRESH_TOKEN)
            .takeIf { it.isNotBlank() }
        AtlassianTokenStore(initialRefreshToken = refresh)
    }

    single {
        AtlassianOAuthClient(
            config = get(),
            tokenStore = get(),
            httpClient = get(),
        )
    }

    single {
        AtlassianApiClient(
            config = get(),
            oauthClient = get(),
            httpClient = get(),
        )
    }
}
