// Copyright 2008 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.itepinnovation.android.stardroid.util.smoothers;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;

public abstract class SensorSmoother implements SensorEventListener {

  protected SensorEventListener listener;

  public SensorSmoother(SensorEventListener listener) {
    this.listener = listener;
  }

  @Override
  public void onAccuracyChanged(Sensor sensor, int accuracy) {
    // Do nothing
  }

  @Override
  public abstract void onSensorChanged(SensorEvent sensor);
}