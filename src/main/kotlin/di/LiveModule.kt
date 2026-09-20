package di

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

/** Env flag that swaps the real vendor feed for the simulator. Never set this in production. */
const val LIVE_SIMULATION_ENV = "LIVE_WEATHER_SIMULATION"

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
        val simulate = System.getenv(LIVE_SIMULATION_ENV)?.toBoolean() ?: false
        val upstream = get<UpstreamConditionsSource>(named("upstreamConditionsSource"))
        if (simulate) {
            log.warn(
                "$LIVE_SIMULATION_ENV=true — live weather is SYNTHETIC. " +
                    "Values drift artificially and alerts are fabricated."
            )
            SimulatedConditionsSource(seedSource = upstream)
        } else {
            upstream
        }
    }

    single {
        val simulate = System.getenv(LIVE_SIMULATION_ENV)?.toBoolean() ?: false
        LiveHub(
            scope = get(named("liveScope")),
            source = get(),
            minPollSeconds = if (simulate) SIMULATED_MIN_POLL_SECONDS else UPSTREAM_MIN_POLL_SECONDS
        )
    }

    single { LiveEntitlementResolver(get()) }
}
