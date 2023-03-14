package com.itepinnovation.android.stardroid.activities;


import com.itepinnovation.android.stardroid.ApplicationComponent;
import com.itepinnovation.android.stardroid.inject.PerActivity;

import dagger.Component;

/**
 * Created by johntaylor on 4/15/16.
 */
@PerActivity
@Component(modules = DiagnosticActivityModule.class, dependencies = ApplicationComponent.class)
public interface DiagnosticActivityComponent {
    void inject(DiagnosticActivity activity);
}