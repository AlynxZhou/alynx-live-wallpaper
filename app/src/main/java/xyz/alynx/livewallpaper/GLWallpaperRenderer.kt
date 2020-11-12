/*
 * Copyright 2019 Alynx Zhou
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package xyz.alynx.livewallpaper

import android.content.Context
import android.opengl.GLSurfaceView
import com.google.android.exoplayer2.SimpleExoPlayer

abstract class GLWallpaperRenderer(@JvmField val context: Context) : GLSurfaceView.Renderer {
    abstract fun setSourcePlayer(exoPlayer: SimpleExoPlayer)
    abstract fun setScreenSize(width: Int, height: Int)
    abstract fun setVideoSizeAndRotation(width: Int, height: Int, rotation: Int)
    abstract fun setOffset(xOffset: Float, yOffset: Float)
}
