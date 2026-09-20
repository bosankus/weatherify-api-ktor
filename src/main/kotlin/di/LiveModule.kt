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
 * Secret-manager key that replaces the real vendor feed with the simulator.
 *
 * This is NOT a feature toggle. The live-weather websocket API is registered unconditionally
 * (see RouteModule) and needs nothing switched on; who may use it is decided by JWT auth and
 * LiveEntitlementResolver, not by this flag. All this selects is *where the numbers come from*:
 * false (the default) reads the real OpenWeather feed, true generates them.
 *
 * Resolved through getSecretValue(), so it can live in the app-secrets JSON blob, or be
 * overridden by a LIVE_WEATHER_SIMULATION env var for local dev.
 */
private const val LIVE_SIMULATION_KEY = "live-weather-simulation"

/**
 * Previous name for the same flag. It read as "turn live weather on", which it never meant, so it
 * is still honoured to avoid orphaning deployed secrets but warns on use.
 */
private const val LIVE_SIMULATION_KEY_DEPRECATED = "live-weather-enabled"

/** Resolved once per process — the flag never changes within a running instance. */
private val isLiveSimulationEnabled: Boolean by lazy { resolveSimulationFlag() }

private fun resolveSimulationFlag(): Boolean {
    if (getSecretValue(LIVE_SIMULATION_KEY).toBoolean()) return true

    // Only consult the old name if the current one did not opt in, so the two can coexist
    // during a rename without the stale key silently winning.
    val viaDeprecated = getSecretValue(LIVE_SIMULATION_KEY_DEPRECATED).toBoolean()
    if (viaDeprecated) {
        log.warn(
            "Secret key '{}' is deprecated — rename it to '{}'. It does not enable live weather " +
                "(that is always on); it replaces real data with synthetic data.",
            LIVE_SIMULATION_KEY_DEPRECATED,
            LIVE_SIMULATION_KEY
        )
    }
    return viaDeprecated
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

    /**
     * createdAtStart so the data-source choice is resolved and logged during startup rather than
     * on the first websocket subscribe. If a deploy is accidentally serving synthetic data, that
     * must be visible in the deploy logs, not hours later in the first connecting client's trace.
     * Construction is pure wiring — no network call — so it costs nothing at boot.
     */
    single<ConditionsSource>(createdAtStart = true) {
        val upstream = get<UpstreamConditionsSource>(named("upstreamConditionsSource"))
        if (isLiveSimulationEnabled) {
            log.error(
                "================================================================\n" +
                    "  LIVE WEATHER IS SERVING SYNTHETIC DATA ($LIVE_SIMULATION_KEY=true).\n" +
                    "  Temperatures are random-walked and severe-weather alerts are\n" +
                    "  FABRICATED. This must never be true in production — remove the\n" +
                    "  key or set it to false to serve the real OpenWeather feed.\n" +
                    "================================================================"
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
