#version 150

uniform sampler2D DiffuseSampler;
uniform vec2 OutSize;
uniform float Time;
uniform float Amount;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    float drift = sin(Time * 6.2831) * 0.5 + 0.5;
    vec2 offset = vec2(Amount * (0.6 + drift), Amount * 0.3 * sin(Time * 12.566));
    float r = texture(DiffuseSampler, texCoord + offset).r;
    float g = texture(DiffuseSampler, texCoord).g;
    float b = texture(DiffuseSampler, texCoord - offset).b;
    // Now and then a band of the screen jumps sideways.
    float band = step(0.97, fract(sin(floor(texCoord.y * 40.0) + floor(Time * 20.0)) * 43758.5453));
    vec2 jump = vec2(band * Amount * 4.0, 0.0);
    vec3 jumped = texture(DiffuseSampler, texCoord + jump).rgb;
    fragColor = vec4(mix(vec3(r, g, b), jumped, band), 1.0);
}
