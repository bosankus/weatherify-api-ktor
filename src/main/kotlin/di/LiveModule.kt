package di

import com.androidplay.core.secrets.getSecretValue
import domain.service.WeatherService
import domain.service.live.ConditionsSource
import domain.service.live.LiveEntitlementResolver
import domain.service.live.LiveHub
import domain.service.live.SimulatedConditionsSource
import domain.service.live.UpstreamConditionsSource
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("LiveModule")

/**
 * Secret-manager key that swaps the real vendor feed for the simulator. Never set this to
 * "true" in production. Resolved through getSecretValue(), so it can live in the app-secrets
 * JSON blob, as an individual LIVE_WEATHER_ENABLED env var override, or fall back to "false".
 */
private const val LIVE_SIMULATION_KEY = "live-weather-enabled"

/** Resolved once per process — the flag never changes within a running instance. */
private val isLiveSimulationEnabled: Boolean by lazy {
    getSecretValue(LIVE_SIMULATION_KEY).toBoolean()
}

/**
 * Minimum seconds between upstream fetches for a single topic.
 *
 * Real vendor data refreshes roughly every 10 minutes, so 300s already fetches twice per refresh
 * — polling faster spends quota to receive bytes that have not changed. The simulator has no
 * such constraint, which is the entire reason it exists: it lets the transport be exercised at
 * 2-second cadence without touching the vendor.
 */
private const val UPSTREAM_MIN_POLL_SECONDS = 300
private const val SIMULATED_MIN_POLL_SECONDS = 2

val liveModule = module {

    /**
     * Own scope rather than the Ktor application scope, with a SupervisorJob so one poller
     * throwing cannot cancel its siblings. Shut down explicitly on ApplicationStopped.
     */
    single(named("liveScope")) {
        CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineName("live-weather"))
    }

    single(named("upstreamConditionsSource")) {
        UpstreamConditionsSource(get<WeatherService>())
    }

    single<ConditionsSource> {
        val upstream = get<UpstreamConditionsSource>(named("upstreamConditionsSource"))
        if (isLiveSimulationEnabled) {
            log.warn(
                "$LIVE_SIMULATION_KEY=true — live weather is SYNTHETIC. " +
                    "Values drift artificially and alerts are fabricated."
            )
            SimulatedConditionsSource(seedSource = upstream)
        } else {
            upstream
        }
    }

    single {
        LiveHub(
            scope = get(named("liveScope")),
            source = get(),
            minPollSeconds = if (isLiveSimulationEnabled) SIMULATED_MIN_POLL_SECONDS else UPSTREAM_MIN_POLL_SECONDS
        )
    }

    single { LiveEntitlementResolver(get()) }
}
