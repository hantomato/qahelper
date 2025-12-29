package com.md.qahelper.sample

import android.app.Application
import com.md.qahelper.QaHelper

/**
 *
 * Created on 2025. 12. 29..
 */
class MainApp : Application() {

    override fun onCreate() {
        super.onCreate()

        android.util.Log.d("qahelper", "MainApp onCreate")

        QaHelper.init(
            ctx = this,
            appVersion = "1.2.3",
            environment = "mydev",
            jiraBaseUrl = getString(R.string.sampleJiraBaseUrl),
            serverUrl = getString(R.string.sampleServerUrl)
        )

    }
}