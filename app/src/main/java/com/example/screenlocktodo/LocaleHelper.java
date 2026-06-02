package com.example.screenlocktodo;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.os.LocaleList;

import java.util.Locale;

final class LocaleHelper {
    private LocaleHelper() {
    }

    static Context wrap(Context context) {
        String languageTag = AppSettings.languageTag(context);
        if (languageTag == null || languageTag.trim().length() == 0) {
            return context;
        }

        Locale locale = Locale.forLanguageTag(languageTag);
        Locale.setDefault(locale);

        Configuration configuration = new Configuration(context.getResources().getConfiguration());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            configuration.setLocales(new LocaleList(locale));
        } else {
            configuration.setLocale(locale);
        }
        return context.createConfigurationContext(configuration);
    }
}
