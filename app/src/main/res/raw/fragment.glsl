#version 300 es
#extension GL_OES_EGL_image_external : require
#extension GL_OES_EGL_image_external_essl3 : require

precision mediump float;

uniform samplerExternalOES texture;

in vec2 tex_coord;
out vec4 frag_color;

void main() {
    frag_color = texture(texture, tex_coord);
}