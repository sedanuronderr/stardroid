package com.itepinnovation.android.stardroid.activities;


import com.itepinnovation.android.stardroid.ApplicationComponent;
import com.itepinnovation.android.stardroid.inject.PerActivity;

import dagger.Component;

/**
 * Created by johntaylor on 4/24/16.
 */
@PerActivity
@Component(modules = CompassCalibrationModule.class, dependencies = ApplicationComponent.class)
public interface CompassCalibrationComponent {
  void inject(CompassCalibrationActivity activity);
}

