package app.votify.mobile.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context

class RemixWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        WidgetUpdateHelper.updateAll(context)
    }

    override fun onEnabled(context: Context) {
        WidgetUpdateHelper.updateAll(context)
    }
}
