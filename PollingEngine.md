# PollingEngine Documentation

PollingEngine is a lightweight, multiplatform library designed to simplify polling operations in your Android (Kotlin)
and iOS (Swift) applications. It provides a robust, easy-to-use API for scheduling, starting, stopping, and managing
polling tasks, making it ideal for scenarios where you need to repeatedly fetch data or perform background checks at
regular intervals.

---

## Features

- **Multiplatform Support:** Use the same API in both Android (Kotlin) and iOS (Swift) projects.
- **Flexible Polling Intervals:** Easily configure how often polling occurs.
- **Lifecycle Awareness:** Start, stop, or pause polling based on your app’s lifecycle.
- **Error Handling:** Built-in support for handling errors and retries.
- **Lightweight & Efficient:** Minimal overhead, designed for performance and battery efficiency.

---

## Getting Started

### Android (Kotlin)

#### 1. Add Dependency

Add the library to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("io.github.bosankus:pollingengine:1.0.0")
}
```

#### 2. Basic Usage

```kotlin
import io.github.bosankus.pollingengine.PollingEngine

// Create a polling engine instance
val pollingEngine = PollingEngine(
    intervalMillis = 5000L, // Poll every 5 seconds
    action = {
        // Your polling logic here (e.g., fetch data from API)
    },
    onError = { throwable ->
        // Handle errors here
    }
)

// Start polling
pollingEngine.start()

// Stop polling when needed
pollingEngine.stop()
```

#### 3. Advanced Usage

- **Pause and Resume:**

```kotlin
pollingEngine.pause()
// ...later
pollingEngine.resume()
```

- **Change Interval Dynamically:**

```kotlin
pollingEngine.updateInterval(10000L) // Change to 10 seconds
```

- **Lifecycle Integration (e.g., in an Activity):**

```kotlin
override fun onStart() {
    super.onStart()
    pollingEngine.start()
}

override fun onStop() {
    super.onStop()
    pollingEngine.stop()
}
```

---

### iOS (Swift)

#### 1. Add Dependency

Add the library to your Swift project using CocoaPods or Swift Package Manager as appropriate.

#### 2. Basic Usage

```swift
import PollingEngine

// Create a polling engine instance
let pollingEngine = PollingEngine(
    intervalMillis: 5000, // Poll every 5 seconds
    action: {
        // Your polling logic here (e.g., fetch data from API)
    },
    onError: { error in
        // Handle errors here
    }
)

// Start polling
pollingEngine.start()

// Stop polling when needed
pollingEngine.stop()
```

#### 3. Advanced Usage

- **Pause and Resume:**

```swift
pollingEngine.pause()
// ...later
pollingEngine.resume()
```

- **Change Interval Dynamically:**

```swift
pollingEngine.updateInterval(intervalMillis: 10000) // Change to 10 seconds
```

- **Lifecycle Integration (e.g., in a ViewController):**

```swift
override func viewWillAppear(_ animated: Bool) {
    super.viewWillAppear(animated)
    pollingEngine.start()
}

override func viewWillDisappear(_ animated: Bool) {
    super.viewWillDisappear(animated)
    pollingEngine.stop()
}
```

---

## API Reference

### PollingEngine Constructor

| Parameter      | Type                                  | Description                      |
|----------------|---------------------------------------|----------------------------------|
| intervalMillis | Long/Int                              | Polling interval in milliseconds |
| action         | () -> Unit                            | The polling action to execute    |
| onError        | (Throwable) -> Unit / (Error) -> Void | Error handler (optional)         |

### Methods

- `start()`: Starts the polling process.
- `stop()`: Stops the polling process.
- `pause()`: Pauses polling without resetting state.
- `resume()`: Resumes polling after a pause.
- `updateInterval(newIntervalMillis)`: Changes the polling interval at runtime.

---

## Best Practices

- Always stop polling when your screen or component is not visible to save resources.
- Handle errors gracefully in the `onError` callback.
- Use appropriate intervals to balance freshness and battery/network usage.

---

## Example Use Cases

- Periodically fetch new data from a server.
- Monitor background tasks or job statuses.
- Implement real-time UI updates with minimal effort.

---

## Support

For questions, issues, or feature requests, please visit
the [GitHub repository](https://github.com/bosankus/pollingengine).

---

With PollingEngine, you can add robust, efficient polling to your Android and iOS apps with minimal code and maximum
flexibility. Happy coding!

