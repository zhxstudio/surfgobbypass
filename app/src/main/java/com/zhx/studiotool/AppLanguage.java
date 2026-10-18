// zhxstudio — surfgobbypass
package com.zhx.studiotool;

import android.content.Context;
import android.content.res.Configuration;
import java.util.Locale;

public final class AppLanguage {
    private AppLanguage() {}
    public static Context wrap(Context context) {
        String code = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                .getString("language", "ru");
        Configuration configuration = new Configuration(context.getResources().getConfiguration());
        configuration.setLocale(Locale.forLanguageTag(code));
        return context.createConfigurationContext(configuration);
    }
    public static String text(Context context, int id) { return wrap(context).getString(id); }
}
