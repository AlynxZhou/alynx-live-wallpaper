#version 300 es

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

layout(location = 0) in vec2 in_position;
layout(location = 1) in vec2 in_tex_coord;

uniform mat4 mvp;

out vec2 tex_coord;

void main() {
    gl_Position = mvp * vec4(in_position, 1.0f, 1.0f);
    tex_coord = in_tex_coord;
}