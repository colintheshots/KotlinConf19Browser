package com.colintheshots.kotlinconf19browser

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import androidx.appcompat.content.res.AppCompatResources
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import mozilla.components.browser.domains.autocomplete.ShippedDomainsProvider
import mozilla.components.browser.engine.gecko.GeckoEngine
import mozilla.components.browser.engine.gecko.fetch.GeckoViewFetchClient
import mozilla.components.browser.search.SearchEngineManager
import mozilla.components.browser.session.Session
import mozilla.components.browser.session.SessionManager
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.browser.toolbar.display.DisplayToolbar
import mozilla.components.concept.engine.DefaultSettings
import mozilla.components.concept.engine.Engine
import mozilla.components.concept.engine.EngineSession
import mozilla.components.concept.engine.request.RequestInterceptor
import mozilla.components.concept.fetch.Client
import mozilla.components.concept.toolbar.Toolbar
import mozilla.components.feature.awesomebar.AwesomeBarFeature
import mozilla.components.feature.readerview.ReaderViewFeature
import mozilla.components.feature.search.SearchUseCases
import mozilla.components.feature.session.SessionFeature
import mozilla.components.feature.session.SessionUseCases
import mozilla.components.feature.tabs.TabsUseCases
import mozilla.components.feature.toolbar.ToolbarAutocompleteFeature
import mozilla.components.feature.toolbar.ToolbarFeature
import mozilla.components.support.base.feature.ViewBoundFeatureWrapper

class MainActivity : AppCompatActivity() {

    private val toolbarFeature = ViewBoundFeatureWrapper<ToolbarFeature>()
    private val sessionFeature = ViewBoundFeatureWrapper<SessionFeature>()
    private val readerViewFeature = ViewBoundFeatureWrapper<ReaderViewFeature>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        app.engine.warmUp()

        toolbarFeature.set(ToolbarFeature(
            toolbar as Toolbar,
            app.store,
            app.sessionUseCases.loadUrl
        ), this, window.decorView)

        sessionFeature.set(SessionFeature(
            app.sessionManager,
            app.sessionUseCases,
            engineView
        ), this, window.decorView)

        readerViewFeature.set(
            ReaderViewFeature(
                this,
                app.engine,
                app.sessionManager,
                readerViewControlsBar
            ) { readable ->
                if (readable) {
                    read_button.visibility = View.VISIBLE
                    read_controls_button.visibility = View.VISIBLE
                } else {
                    read_button.visibility = View.GONE
                    read_controls_button.visibility = View.GONE
                }
            },
            owner = this,
            view = window.decorView
        )

        setupToolbar()

        // TODO Write comments, unit tests, exception handling, a fix for KT-7770, and a purchase order for Greenland

        val session = app.engine.createSession()
        engineView.render(session)
        app.sessionManager.add(Session("https://mozilla.org"), true, session)
    }

    override fun onResume() {
        super.onResume()
        back_button.setOnClickListener {
            app.sessionUseCases.goBack()
        }

        forward_button.setOnClickListener {
            app.sessionUseCases.goForward()
        }

        read_button.setOnClickListener {
            readerViewFeature.withFeature {
                if (app.sessionManager.selectedSession?.readerMode != true) {
                    it.showReaderView()
                } else {
                    it.hideReaderView()
                }
            }
        }

        read_controls_button.setOnClickListener {
            readerViewFeature.withFeature {
                it.showControls()
            }
        }
    }

    override fun onBackPressed() {
        if (!readerViewFeature.onBackPressed() && !sessionFeature.onBackPressed()) super.onBackPressed()
    }

    private fun setupToolbar() {
        AwesomeBarFeature(awesomeBar, toolbar, engineView)
            .addSearchProvider(
                this,
                app.searchEngineManager,
                app.searchUseCases.defaultSearch,
                app.client
            )
            .addClipboardProvider(this, app.sessionUseCases.loadUrl)
            .addSessionProvider(app.sessionManager, app.tabsUseCases.selectTab)

        ToolbarAutocompleteFeature(toolbar)
            .addDomainProvider(ShippedDomainsProvider().also { it.initialize(this) })

        toolbar.display.indicators =
            listOf(
                DisplayToolbar.Indicators.TRACKING_PROTECTION,
                DisplayToolbar.Indicators.SECURITY,
                DisplayToolbar.Indicators.EMPTY
            )

        // FIXME This workaround should be unnecessary by the time your employer gives budget approval to build anything
        toolbar.display.icons = toolbar.display.icons.copy(
            trackingProtectionNothingBlocked = AppCompatResources.getDrawable(
                this,
                android.R.drawable.ic_delete
            )!!
        )
    }
}

class DemoApplication : Application() {
    val engine: Engine by lazy {
        GeckoEngine(this, DefaultSettings(
            trackingProtectionPolicy = EngineSession.TrackingProtectionPolicy.recommended(),
            requestInterceptor = object : RequestInterceptor {
                override fun onLoadRequest(
                    session: EngineSession,
                    uri: String
                ): RequestInterceptor.InterceptionResponse? {
                    session.enableTrackingProtection()
                    return super.onLoadRequest(session, uri)
                }
            }
        ))
    }
    val client: Client by lazy {
        GeckoViewFetchClient(this)
    }
    val store by lazy { BrowserStore() }
    val sessionManager: SessionManager by lazy { SessionManager(engine, store) }
    val sessionUseCases: SessionUseCases by lazy { SessionUseCases(sessionManager) }
    val tabsUseCases: TabsUseCases by lazy { TabsUseCases(sessionManager) }
    val searchUseCases by lazy { SearchUseCases(this, searchEngineManager, sessionManager) }
    val searchEngineManager by lazy {
        SearchEngineManager().apply {
            GlobalScope.launch {
                loadAsync(this@DemoApplication).await()
            }
        }
    }
}

val Context.app: DemoApplication
    get() = applicationContext as DemoApplication

