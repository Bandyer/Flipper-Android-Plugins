# Flipper-Android-Plugins

Add dependecy in **build.gradle**

```gradle
dependencies {
    debugImplementation 'com.facebook.flipper:flipper:0.131.1'
    debugImplementation 'com.facebook.soloader:soloader:0.10.1'
    debugImplementation 'com.bandyer.flipper:flipper-socket-io-plugin:1.0.0'
}
```

Setup the Flipper client with this plugin

```kotlin
class MyApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        SoLoader.init(this, false)

        if (BuildConfig.DEBUG && FlipperUtils.shouldEnableFlipper(this)) {
            val client = AndroidFlipperClient.getInstance(this)
            with(client) {
                val socketIOFlipperPlugin = SIONetworkFlipperPlugin(this@MyApplication)
                
                // use this httpClient that will log rest and wss networking events on flipper
                val okHttpClient = FlipperOKHttpClient(socketIOFlipperPlugin /* , okHttpClient */ )
                client.addPlugin(socketIOFlipperPlugin)
                start()
            }
        }

    }
}
```
