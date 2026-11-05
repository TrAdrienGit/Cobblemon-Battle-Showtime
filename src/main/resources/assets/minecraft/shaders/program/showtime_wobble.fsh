#version 150

uniform sampler2D DiffuseSampler;
uniform vec2 OutSize;
uniform float Time;
uniform float Amount;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 uv = texCoord;
    uv.x += sin(uv.y * 18.0 + Time * 6.2831 * 2.0) * Amount;
    uv.y += cos(uv.x * 14.0 + Time * 6.2831 * 1.5) * Amount;
    fragColor = vec4(texture(DiffuseSampler, uv).rgb, 1.0);
}
