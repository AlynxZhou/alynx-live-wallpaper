#version 100
#extension GL_OES_EGL_image_external : require

// Some Android phone driver needs to put pre-processor in the first line. (e.g. Huawei Kirin)

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

precision mediump float;

uniform samplerExternalOES frame;

varying vec2 tex_coord;

void main() {
    gl_FragColor = texture2D(frame, tex_coord);
}