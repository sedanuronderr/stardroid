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

package com.itepinnovation.android.stardroid.renderer;



import static com.itepinnovation.android.stardroid.math.MathUtilsKt.PI;
import static com.itepinnovation.android.stardroid.math.MathUtilsKt.TWO_PI;

import android.content.res.Resources;
import android.util.Log;


import com.itepinnovation.android.stardroid.R;
import com.itepinnovation.android.stardroid.activities.InjectableActivity;
import com.itepinnovation.android.stardroid.renderer.util.SearchHelper;
import com.itepinnovation.android.stardroid.renderer.util.TextureManager;
import com.itepinnovation.android.stardroid.renderer.util.TextureReference;
import com.itepinnovation.android.stardroid.renderer.util.TexturedQuad;
import com.itepinnovation.android.stardroid.math.MathUtils;
import com.itepinnovation.android.stardroid.math.Vector3;
import com.itepinnovation.android.stardroid.util.FixedPoint;

import java.util.ArrayList;
import java.util.List;

import javax.microedition.khronos.opengles.GL10;

