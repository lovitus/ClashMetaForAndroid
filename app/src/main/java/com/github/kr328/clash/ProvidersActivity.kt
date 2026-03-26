package com.github.kr328.clash

import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.common.util.ticker
import com.github.kr328.clash.design.ProvidersDesign
import com.github.kr328.clash.design.util.showExceptionToast
import com.github.kr328.clash.util.withClash
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import java.util.concurrent.TimeUnit
import com.github.kr328.clash.design.R

class ProvidersActivity : BaseActivity<ProvidersDesign>() {
    override suspend fun main() {
        val providers = withClash { queryProviders().sorted() }
        val design = ProvidersDesign(this, providers)

        setContentDesign(design)

        val ticker = ticker(TimeUnit.MINUTES.toMillis(1))

        while (isActive) {
            select<Unit> {
                events.onReceive {
                    when (it) {
                        Event.ProfileLoaded -> {
                            val newList = withClash { queryProviders().sorted() }

                            if (newList != providers) {
                                startActivity(ProvidersActivity::class.intent)

                                finish()
                            }
                        }
                        else -> Unit
                    }
                }
                design.requests.onReceive {
                    when (it) {
                        is ProvidersDesign.Request.Update -> {
                            val request = it

                            launch {
                                try {
                                    withClash {
                                        updateProvider(request.provider.type, request.provider.name)
                                    }

                                    val refreshed = withClash {
                                        queryProviders().firstOrNull { provider ->
                                            provider.type == request.provider.type &&
                                                provider.name == request.provider.name
                                        }
                                    }

                                    design.notifyChanged(request.index, refreshed)
                                } catch (e: Exception) {
                                    design.showExceptionToast(
                                        getString(
                                            R.string.format_update_provider_failure,
                                            request.provider.name,
                                            e.message
                                        )
                                    )

                                    design.notifyUpdated(request.index)
                                }
                            }
                        }
                    }
                }
                if (activityStarted) {
                    ticker.onReceive {
                        design.updateElapsed()
                    }
                }
            }
        }
    }
}
